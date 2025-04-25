package option

import (
	"encoding/json"
	"errors"
	"fmt"
	"io/ioutil"
	"net/http"
	"strconv"
	"time"

	"github.com/gofiber/fiber/v2/log"

	"banka1.com/db"
	"banka1.com/types"
)

type YahooOptionsApiResponse struct {
	OptionChain struct {
		Result []struct {
			UnderlyingSymbol string    `json:"underlyingSymbol"`
			ExpirationDates  []int64   `json:"expirationDates"`
			Strikes          []float64 `json:"strikes"`
			Quote            struct {
				Symbol             string  `json:"symbol"`
				ShortName          string  `json:"shortName"`
				LongName           string  `json:"longName"`
				RegularMarketPrice float64 `json:"regularMarketPrice"`
				Bid                float64 `json:"bid"`
				Ask                float64 `json:"ask"`
			} `json:"quote"`
			Options []struct {
				ExpirationDate int64 `json:"expirationDate"`
				HasMiniOptions bool  `json:"hasMiniOptions"`
				Calls          []struct {
					ContractSymbol    string  `json:"contractSymbol"`
					Strike            float64 `json:"strike"`
					LastPrice         float64 `json:"lastPrice"`
					Change            float64 `json:"change"`
					PercentChange     float64 `json:"percentChange"`
					Volume            int     `json:"volume"`
					OpenInterest      int     `json:"openInterest"`
					Bid               float64 `json:"bid"`
					Ask               float64 `json:"ask"`
					ImpliedVolatility float64 `json:"impliedVolatility"`
				} `json:"calls"`
				Puts []struct {
					ContractSymbol    string  `json:"contractSymbol"`
					Strike            float64 `json:"strike"`
					LastPrice         float64 `json:"lastPrice"`
					Change            float64 `json:"change"`
					PercentChange     float64 `json:"percentChange"`
					Volume            int     `json:"volume"`
					OpenInterest      int     `json:"openInterest"`
					Bid               float64 `json:"bid"`
					Ask               float64 `json:"ask"`
					ImpliedVolatility float64 `json:"impliedVolatility"`
				} `json:"puts"`
			} `json:"options"`
		} `json:"result"`
	} `json:"optionChain"`
}

func getYahooCookie() (*http.Cookie, error) {
	client := &http.Client{}
	req, _ := http.NewRequest("GET", "https://fc.yahoo.com", nil)
	req.Header.Set("User-Agent", "Mozilla/5.0")

	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	for _, cookie := range resp.Cookies() {
		fmt.Printf("Cookie: %s = %s\n", cookie.Name, cookie.Value)
		if cookie.Name == "A3" {
			return cookie, nil
		}
	}
	return nil, errors.New("Yahoo cookie 'A3' not found")
}

func getYahooCrumb(cookie *http.Cookie) (string, error) {
	client := &http.Client{}
	req, _ := http.NewRequest("GET", "https://query2.finance.yahoo.com/v1/test/getcrumb", nil)
	req.AddCookie(cookie)
	req.Header.Set("User-Agent", "Mozilla/5.0")

	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	crumb, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	return string(crumb), nil
}

func FetchYahoo(ticker string) (YahooOptionsApiResponse, error) {
	cookie, err := getYahooCookie()
	if err != nil {
		return YahooOptionsApiResponse{}, err
	}
	crumb, err := getYahooCrumb(cookie)
	if err != nil {
		return YahooOptionsApiResponse{}, err
	}

	url := fmt.Sprintf("https://query1.finance.yahoo.com/v7/finance/options/%s?crumb=%s", ticker, crumb)
	log.Infof("Fetching Yahoo options for ticker: %s", ticker)
	log.Infof("URL: %s", url)

	client := &http.Client{}
	req, _ := http.NewRequest("GET", url, nil)
	req.AddCookie(cookie)
	req.Header.Set("User-Agent", "Mozilla/5.0")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("Referer", "https://finance.yahoo.com/quote/"+ticker)

	resp, err := client.Do(req)
	if err != nil {
		return YahooOptionsApiResponse{}, err
	}
	defer resp.Body.Close()

	body, err := ioutil.ReadAll(resp.Body)
	if err != nil {
		return YahooOptionsApiResponse{}, err
	}

	var yahooResp YahooOptionsApiResponse
	if err := json.Unmarshal(body, &yahooResp); err != nil {
		return YahooOptionsApiResponse{}, err
	}

	return yahooResp, nil
}
func LoadAllOptions() error {
	// get all listings
	var listings []types.Listing
	if err := db.DB.Where("type = ?", "Stock").Find(&listings).Error; err != nil {
		return err
	}
	log.Infof("Loaded %d listings", len(listings))
	// delete all options
	log.Info("Deleting all options")
	if err := db.DB.Exec("DELETE FROM option").Error; err != nil {
		return err
	}
	for _, listing := range listings {
		yahooResp, err := FetchYahoo(listing.Ticker)
		if err != nil {
			log.Infof("Error: %v", err)
			continue
		}
		log.Infof("Fetched Yahoo options for ticker: %s", listing.Ticker)
		err = SaveOptionsToDB(listing.Ticker, yahooResp)
		if err != nil {
			log.Infof("Error saving options: %v", err)
			continue
		}
	}

	return nil
}

func formatStrikePrice(strike float64) string {
	return fmt.Sprintf("%08d", int(strike*100)) // Convert to cents and pad with zeros
}

// Generate a unique ticker for the option
func GenerateOptionTicker(baseTicker string, expiration time.Time, optionType string, strike float64) string {
	// Extract year (YY), month (MM), and day (DD)
	year := expiration.Year() % 100  // Get last two digits of year (2024 → 24)
	month := int(expiration.Month()) // Month as number (06 for June)
	day := expiration.Day()          // Day of the month (21)

	// Convert strike price to correct format
	strikePrice := formatStrikePrice(strike)

	// Construct the final ticker
	return fmt.Sprintf("%s%02d%02d%02d%s%s", baseTicker, year, month, day, optionType, strikePrice)
}

func SaveOptionsToDB(ticker string, yahooResp YahooOptionsApiResponse) error {
	var baseListing types.Listing
	if err := db.DB.Where("ticker = ?", ticker).First(&baseListing).Error; err != nil {
		return errors.New("listing not found for ticker: " + ticker)
	}

	fmt.Printf("Base listing: %s\n", baseListing.Ticker)

	log.Infof("Yahoo response length: %d", len(yahooResp.OptionChain.Result))

	for _, result := range yahooResp.OptionChain.Result {
		log.Infof("Processing ticker: %s", result.UnderlyingSymbol)
		for _, opt := range result.Options {
			log.Infof("Processing option: %s", opt.ExpirationDate)
			expirationDate := time.Unix(opt.ExpirationDate, 0)

			for _, call := range opt.Calls {
				log.Infof("Processing call option: %s", call.ContractSymbol)
				optionTicker := GenerateOptionTicker(ticker, expirationDate, "C", call.Strike)

				var optionListing types.Listing
				if err := db.DB.Where("ticker = ?", optionTicker).First(&optionListing).Error; err != nil {
					optionListing = types.Listing{
						Ticker:       optionTicker,
						Name:         baseListing.Name + " Option",
						ExchangeID:   baseListing.ExchangeID,
						LastRefresh:  time.Now(),
						Price:        float32(call.LastPrice),
						Ask:          float32(call.Ask),
						Bid:          float32(call.Bid),
						Type:         "Option",
						Subtype:      "Call Option",
						ContractSize: 100,
					}
					if err := db.DB.Create(&optionListing).Error; err != nil {
						return err
					}
				}

				option := types.Option{
					ListingID:      optionListing.ID,
					OptionType:     "Call",
					StrikePrice:    call.Strike,
					ImpliedVol:     call.ImpliedVolatility,
					OpenInterest:   int64(call.OpenInterest),
					SettlementDate: expirationDate,
					ContractSize:   100,
				}
				if err := db.DB.Create(&option).Error; err != nil {
					return err
				}
			}

			for _, put := range opt.Puts {
				optionTicker := GenerateOptionTicker(ticker, expirationDate, "P", put.Strike)

				var optionListing types.Listing
				if err := db.DB.Where("ticker = ?", optionTicker).First(&optionListing).Error; err != nil {
					optionListing = types.Listing{
						Ticker:       optionTicker,
						Name:         baseListing.Name + " Option",
						ExchangeID:   baseListing.ExchangeID,
						LastRefresh:  time.Now(),
						Price:        float32(put.LastPrice),
						Ask:          float32(put.Ask),
						Bid:          float32(put.Bid),
						Type:         "Option",
						Subtype:      "Put Option",
						ContractSize: 100,
					}
					if err := db.DB.Create(&optionListing).Error; err != nil {
						return err
					}
				}

				option := types.Option{
					ListingID:      optionListing.ID,
					OptionType:     "Put",
					StrikePrice:    put.Strike,
					ImpliedVol:     put.ImpliedVolatility,
					OpenInterest:   int64(put.OpenInterest),
					SettlementDate: expirationDate,
					ContractSize:   100,
				}
				if err := db.DB.Create(&option).Error; err != nil {
					return err
				}
			}
		}
	}
	return nil
}

func GetOptionsForTicker(ticker string) ([]types.Option, error) {
	var options []types.Option
	if err := db.DB.Where("ticker = ?", ticker).Find(&options).Error; err != nil {
		return nil, err
	}
	return options, nil
}

func GetOptionsForListingID(listingID uint) ([]types.Option, error) {
	var options []types.Option
	if err := db.DB.Where("listing_id = ?", listingID).Find(&options).Error; err != nil {
		return nil, err
	}

	return options, nil
}

func GetOptionsForSymbol(symbol string) ([]types.Option, error) {
	var options []types.Option
	if err := db.DB.Where("ticker LIKE ?", symbol+"%").Find(&options).Error; err != nil {
		return nil, err
	}

	return options, nil
}

func ParseOptionSettlementDate(ticker string) (time.Time, error) {
	if len(ticker) < 15 {
		return time.Time{}, fmt.Errorf("invalid option ticker format")
	}

	yearStr := ticker[len(ticker)-15 : len(ticker)-13]
	monthStr := ticker[len(ticker)-13 : len(ticker)-11]
	dayStr := ticker[len(ticker)-11 : len(ticker)-9]

	year, err := strconv.Atoi(yearStr)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid year in option ticker: %w", err)
	}
	month, err := strconv.Atoi(monthStr)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid month in option ticker: %w", err)
	}
	day, err := strconv.Atoi(dayStr)
	if err != nil {
		return time.Time{}, fmt.Errorf("invalid day in option ticker: %w", err)
	}

	return time.Date(2000+year, time.Month(month), day, 0, 0, 0, 0, time.UTC), nil
}
