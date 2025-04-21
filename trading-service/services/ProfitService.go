package services

import (
	"banka1.com/db"
	"banka1.com/dto"
	"banka1.com/types"
	"fmt"
)

type buyLot struct {
	Quantity int
	Price    float64
}

func CalculateRealizedProfit(userID uint) (*dto.RealizedProfitResponse, error) {
	var transactions []types.Transaction
	if err := db.DB.
		Where("buyer_id = ? OR seller_id = ?", userID, userID).
		Order("created_at").
		Find(&transactions).Error; err != nil {
		return nil, err
	}

	if len(transactions) == 0 {
		return nil, fmt.Errorf("Korisnik nema transakcija. Ne može se izračunati profit.")
	}

	buyMap := map[uint][]buyLot{}   // securityID -> queue of buys
	profitMap := map[uint]float64{} // securityID -> total profit
	tickerMap := map[uint]string{}  // securityID -> ticker

	for _, tx := range transactions {
		// Lazy-load ticker ako nedostaje
		if _, ok := tickerMap[tx.SecurityID]; !ok {
			var sec types.Security
			if err := db.DB.Select("ticker").First(&sec, tx.SecurityID).Error; err == nil {
				tickerMap[tx.SecurityID] = sec.Ticker
			} else {
				tickerMap[tx.SecurityID] = "UNKNOWN"
			}
		}

		if tx.BuyerID == userID {
			// dodaj u FIFO
			buyMap[tx.SecurityID] = append(buyMap[tx.SecurityID], buyLot{
				Quantity: tx.Quantity,
				Price:    tx.PricePerUnit,
			})
		} else if tx.SellerID == userID {
			remaining := tx.Quantity
			queue := buyMap[tx.SecurityID]

			for i := 0; i < len(queue) && remaining > 0; i++ {
				buy := &queue[i]
				matchQty := min(remaining, buy.Quantity)
				profit := float64(matchQty) * (tx.PricePerUnit - buy.Price)
				profitMap[tx.SecurityID] += profit
				buy.Quantity -= matchQty
				remaining -= matchQty
			}

			// izbaci potrošene
			filtered := []buyLot{}
			for _, b := range queue {
				if b.Quantity > 0 {
					filtered = append(filtered, b)
				}
			}
			buyMap[tx.SecurityID] = filtered
		}
	}

	var perSec []dto.SecurityProfit
	var total float64
	for secID, profit := range profitMap {
		perSec = append(perSec, dto.SecurityProfit{
			SecurityID: secID,
			Ticker:     tickerMap[secID],
			Profit:     profit,
		})
		total += profit
	}

	return &dto.RealizedProfitResponse{
		UserID:      userID,
		TotalProfit: total,
		PerSecurity: perSec,
	}, nil
}

func min(a, b int) int {
	if a < b {
		return a
	}
	return b
}
