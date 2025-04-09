package cron

import (
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"os"
	"time"

	"banka1.com/db"

	"github.com/gofiber/fiber/v2/log"
	"gorm.io/gorm"

	"banka1.com/types"
	"github.com/robfig/cron/v3"
)

type Employee struct {
	ID          int      `json:"id"`
	FirstName   string   `json:"firstName"`
	LastName    string   `json:"lastName"`
	Email       string   `json:"email"`
	Department  string   `json:"department"`
	Position    string   `json:"position"`
	Active      bool     `json:"active"`
	Permissions []string `json:"permissions"`
}

type APIResponse struct {
	Success bool       `json:"success"`
	Data    []Employee `json:"data"`
}

// cron posao koji resetuje limit agentu svakog dana u 23 59
func StartScheduler() {
	SnapshotListingsToHistory()

	c := cron.New(cron.WithSeconds())

	_, err := c.AddFunc("0 59 23 * * *", func() {
		resetDailyLimits()
	})

	_, err = c.AddFunc("0 1 0 * * *", func() {
		expireOldOptionContracts()
	})

	_, err = c.AddFunc("0/15 * * * * * ", func() {
		createNewActuaries()
	})

	_, err = c.AddFunc("0 0 0 * * *", func() {
		SnapshotListingsToHistory()
	})

	if err != nil {
		log.Errorf("Greska pri pokretanju cron job-a:", err)
		return
	}

	c.Start()
}

func SnapshotListingsToHistory() error {
	var listings []types.Listing
	if err := db.DB.Find(&listings).Error; err != nil {
		return err
	}

	today := time.Now().Truncate(24 * time.Hour)

	for _, l := range listings {
		// proveri da li već postoji
		var existing types.ListingHistory
		err := db.DB.
			Where("ticker = ? AND snapshot_date = ?", l.Ticker, today).
			First(&existing).Error

		if err == nil {
			continue // već postoji → preskoči
		}

		if !errors.Is(err, gorm.ErrRecordNotFound) {
			return err // neki drugi error
		}

		history := types.ListingHistory{
			Ticker:       l.Ticker,
			Name:         l.Name,
			ExchangeID:   l.ExchangeID,
			LastRefresh:  l.LastRefresh,
			Price:        l.Price,
			Ask:          l.Ask,
			Bid:          l.Bid,
			Type:         l.Type,
			Subtype:      l.Subtype,
			ContractSize: l.ContractSize,
			SnapshotDate: today,
		}
		if err := db.DB.Create(&history).Error; err != nil {
			return err
		}
	}

	return nil
}

func expireOldOptionContracts() {
	now := time.Now()

	var contracts []types.OptionContract
	if err := db.DB.Where("settlement_at < ? AND status = ?", now, "active").Find(&contracts).Error; err != nil {
		log.Errorf("Greška pri pronalaženju ugovora za expirovanje: %v", err)
		return
	}

	for _, contract := range contracts {
		contract.Status = "expired"
		if err := db.DB.Save(&contract).Error; err != nil {
			log.Errorf("Greška pri expirovanju ugovora ID %d: %v", contract.ID, err)
		} else {
			log.Infof("Ugovor ID %d označen kao 'expired'", contract.ID)
		}
	}
}

func resetDailyLimits() {
	db.DB.Model(&types.Actuary{}).Where("role = ?", "agent").Update("usedLimit", 0)
}

func createNewActuaries() {
	data, err := GetActuaries()

	if err != nil {
		return
	}

	if len(data.Data) == 0 {
		return
	}
	for _, actuaryData := range data.Data {
		newActuary := employeeToActuary(actuaryData)

		var existingActuary types.Actuary
		err := db.DB.Where("user_id = ?", newActuary.UserID).First(&existingActuary).Error
		if err != nil {
			if errors.Is(err, gorm.ErrRecordNotFound) {
				if result := db.DB.Create(&newActuary); result.Error != nil {
					log.Errorf("Error creating actuary %v: %v", newActuary, result.Error)
				} else {
					log.Infof("Created new actuary: %v", newActuary)
				}
			} else {
				// Some other error occurred
				log.Errorf("Error checking actuary existence: %v", err)
			}
		} else {
		}
	}
}

func employeeToActuary(employee Employee) types.Actuary {
	actuary := types.Actuary{
		UserID:      uint(employee.ID),
		Department:  employee.Department,
		FullName:    employee.FirstName + " " + employee.LastName,
		Email:       employee.Email,
		LimitAmount: 100000,
	}
	return actuary
}

func GetActuaries() (*APIResponse, error) {
	basePath := os.Getenv("USER_SERVICE")
	url := basePath + "/api/users/employees/actuaries"

	resp, err := http.Get(url)
	if err != nil {
		log.Infof("Failed to fetch %s: %v\n", url, err)
		return nil, err
	}
	defer func(Body io.ReadCloser) {
		err := Body.Close()
		if err != nil {

		}
	}(resp.Body)

	if resp.StatusCode != 200 {
		log.Infof("Error fetching %s: HTTP %d\n", url, resp.StatusCode)
		return nil, err
	}

	var apiResponse *APIResponse
	if err := json.NewDecoder(resp.Body).Decode(&apiResponse); err != nil {
		log.Infof("Failed to parse JSON: %v\n", err)
		return nil, err
	}
	return apiResponse, nil
}
