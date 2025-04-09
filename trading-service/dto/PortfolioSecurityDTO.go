package dto

type PortfolioSecurityDTO struct {
	Ticker       string  `json:"ticker"`
	Type         string  `json:"type"`
	Symbol       string  `json:"symbol"`
	Amount       int     `json:"amount"`
	Price        float64 `json:"price"`
	Profit       float64 `json:"profit"`
	LastModified int64   `json:"last_modified"`
	Public       int     `json:"public"`
}
