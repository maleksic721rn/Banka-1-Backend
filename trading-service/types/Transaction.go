package types

import "time"

type Transaction struct {
	ID           uint `gorm:"primaryKey"`
	OrderID      uint
	ContractID   uint
	BuyerID      uint      `gorm:"not null"`
	SellerID     uint      `gorm:"not null"`
	SecurityID   uint      `gorm:"not null"`
	Quantity     int       `gorm:"not null"`
	PricePerUnit float64   `gorm:"not null"`
	Fee          float64   `gorm:"not null;default:0"`
	TotalPrice   float64   `gorm:"not null"`
	CreatedAt    time.Time `gorm:"autoCreateTime"`
}

func (Transaction) TableName() string {
	return "transactions"
}
