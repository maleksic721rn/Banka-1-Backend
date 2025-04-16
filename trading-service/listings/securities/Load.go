package securities

import (
	"banka1.com/db"
	"banka1.com/types"
	"log"
	"time"
)

func LoadAvailableSecurities() {
	var listings []types.Listing
	if err := db.DB.Preload("Exchange").Find(&listings).Error; err != nil {
		log.Println("Cannot load listings:", err)
		return
	}

	for _, listing := range listings {
		security, err := listingToSecurity(&listing)
		if err != nil || security == nil {
			log.Println("Error converting listings:", listing.ID, err)
			continue
		}

		var existing types.Security
		if err := db.DB.First(&existing, "id = ?", security.ID).Error; err == nil {
			log.Printf("Security already exists ID=%d, Ticker=%s", existing.ID, existing.Ticker)
			continue
		}

		if err := db.DB.Create(security).Error; err != nil {
			log.Printf("Failed insert for Security ID=%d: %v", security.ID, err)
		} else {
			log.Printf("Added security: ID=%d, Ticker=%s", security.ID, security.Ticker)
		}
	}
}

func listingToSecurity(l *types.Listing) (*types.Security, error) {
	var security types.Security
	previousClose := getPreviousCloseForListing(l.ID)
	switch l.Type {
	case "Stock":
		{
			security = types.Security{
				ID:        l.ID,
				Ticker:    l.Ticker,
				Name:      l.Name,
				Type:      l.Type,
				Exchange:  l.Exchange.Name,
				LastPrice: float64(l.Price),
				AskPrice:  float64(l.Ask),
				BidPrice:  float64(l.Bid),
				//Volume:        int64(l.ContractSize * 10),
				ContractSize:  int64(l.ContractSize),
				PreviousClose: previousClose,
			}
		}
	case "Forex":
		{
			security = types.Security{
				ID:        l.ID,
				Ticker:    l.Ticker,
				Name:      l.Name,
				Type:      l.Type,
				Exchange:  l.Exchange.Name,
				LastPrice: float64(l.Price),
				AskPrice:  float64(l.Ask),
				BidPrice:  float64(l.Bid),
				//Volume:        int64(l.ContractSize * 10),
				ContractSize:  int64(l.ContractSize),
				PreviousClose: previousClose,
			}
		}
	case "Future":
		{
			var future types.FuturesContract
			if result := db.DB.Where("listing_id = ?", l.ID).First(&future); result.Error != nil {
				return nil, result.Error
			}
			settlementDate := future.SettlementDate.Format("2006-01-02")
			security = types.Security{
				ID:        l.ID,
				Ticker:    l.Ticker,
				Name:      l.Name,
				Type:      l.Type,
				Exchange:  l.Exchange.Name,
				LastPrice: float64(l.Price),
				AskPrice:  float64(l.Ask),
				BidPrice:  float64(l.Bid),
				//Volume:         int64(l.ContractSize * 10),
				SettlementDate: &settlementDate,
				ContractSize:   int64(l.ContractSize),
				PreviousClose:  previousClose,
			}
		}
	case "Option":
		{
			var option types.Option
			if result := db.DB.Where("listing_id = ?", l.ID).First(&option); result.Error != nil {
				return nil, result.Error
			}
			settlementDate := option.SettlementDate.Format("2006-01-02")
			security = types.Security{
				ID:        l.ID,
				Ticker:    l.Ticker,
				Name:      l.Name,
				Type:      l.Type,
				Exchange:  l.Exchange.Name,
				LastPrice: float64(l.Price),
				AskPrice:  float64(l.Ask),
				BidPrice:  float64(l.Bid),
				//Volume:         int64(l.ContractSize * 10),
				StrikePrice:    &option.StrikePrice,
				OptionType:     &option.OptionType,
				SettlementDate: &settlementDate,
				ContractSize:   int64(l.ContractSize),
				PreviousClose:  previousClose,
			}

		}

	}
	return &security, nil
}

func getPreviousCloseForListing(listingID uint) float64 {
	var dailyInfo types.ListingDailyPriceInfo

	yesterday := time.Now().AddDate(0, 0, -1)

	err := db.DB.
		Where("listing_id = ? AND DATE(date) = ?", listingID, yesterday.Format("2006-01-02")).
		Order("date DESC").
		First(&dailyInfo).Error

	if err == nil {
		return dailyInfo.Price
	}

	err = db.DB.
		Where("listing_id = ?", listingID).
		Order("date DESC").
		First(&dailyInfo).Error

	if err == nil {
		return dailyInfo.Price
	}

	return 0
}
