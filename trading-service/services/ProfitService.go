package services

import (
	"fmt"
	"strconv"

	"banka1.com/db"
	"banka1.com/dto"
	"banka1.com/types"
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

func CalculateBankProfitByMonth() (*[]dto.MonthlyProfitResponse, error) {
	rows, err := db.DB.Raw(`
SELECT m, COALESCE(SUM(sell - buy), 0), COALESCE(SUM(fee), 0)
FROM (SELECT
TO_CHAR(created_at, 'YYYY-MM') AS m,
fee,
CASE WHEN buyer_id IN (SELECT user_id FROM actuary) THEN total_price ELSE 0 END AS buy,
CASE WHEN seller_id IN (SELECT user_id FROM actuary) THEN total_price ELSE 0 END AS sell
FROM transactions)
GROUP BY m
ORDER BY m;`).Rows()
	defer rows.Close()

	if err != nil {
		return nil, fmt.Errorf("Neuspelo izvrsavanje upita: %w", err)
	}

	var responses []dto.MonthlyProfitResponse

	for rows.Next() {
		var yearMonth string
		var actuaryProfit float64
		var fees float64

		err := rows.Scan(&yearMonth, &actuaryProfit, &fees)
		if err != nil {
			return nil, fmt.Errorf("Neuspelo uzimanje reda iz baze: %w", err)
		}

		year, err := strconv.ParseUint(yearMonth[:4], 10, 0)
		if err != nil {
			return nil, err
		}

		month, err := strconv.ParseUint(yearMonth[5:], 10, 0)
		if err != nil {
			return nil, err
		}

		responses = append(responses, dto.MonthlyProfitResponse{
			Year:          uint(year),
			Month:         uint(month),
			ActuaryProfit: actuaryProfit,
			Fees:          fees,
			Total:         actuaryProfit + fees,
		})
	}

	return &responses, nil
}

func CalculateBankProfitTotal() (*dto.TotalProfitResponse, error) {
	var actuaryProfit float64
	var fees float64

	err := db.DB.Raw(`
SELECT
COALESCE(SUM(
CASE WHEN seller_id IN (SELECT user_id FROM actuary) THEN total_price ELSE 0 END
-
CASE WHEN buyer_id IN (SELECT user_id FROM actuary) THEN total_price ELSE 0 END)
, 0),
COALESCE(SUM(fee), 0)
FROM transactions;`).Row().Scan(&actuaryProfit, &fees)

	if err != nil {
		return nil, fmt.Errorf("Neuspelo izvrsavanje upita: %w", err)
	}

	return &dto.TotalProfitResponse{
		ActuaryProfit: actuaryProfit,
		Fees:          fees,
		Total:         actuaryProfit + fees,
	}, nil

}
