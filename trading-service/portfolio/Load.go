package portfolio

import (
	"banka1.com/db"
	"banka1.com/types"
	"log"
)

func LoadPortfolios() {
	p1 := types.Portfolio{
		UserID:        3,
		SecurityID:    1,
		Quantity:      10,
		PurchasePrice: 150.00,
		PublicCount:   0,
	}

	p2 := types.Portfolio{
		UserID:        3,
		SecurityID:    2,
		Quantity:      5,
		PurchasePrice: 180.00,
		PublicCount:   0,
	}

	if err := db.DB.FirstOrCreate(&p1, types.Portfolio{
		UserID:     p1.UserID,
		SecurityID: p1.SecurityID,
	}).Error; err != nil {
		log.Printf("Failed to insert p1: %v\\n", err)
	} else {
		log.Println("Portfolio p1 inserted or already exists.")
	}

	if err := db.DB.FirstOrCreate(&p2, types.Portfolio{
		UserID:     p2.UserID,
		SecurityID: p2.SecurityID,
	}).Error; err != nil {
		log.Printf("Failed to insert p2: %v\\n", err)
	} else {
		log.Println("Portfolio p2 inserted or already exists.")
	}
}
