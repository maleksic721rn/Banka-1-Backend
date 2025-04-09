package types

type SecurityResponse struct {
	Security      string  `json:"security"`
	Symbol        string  `json:"symbol"`
	Amount        int     `json:"amount"`
	PurchasePrice float64 `json:"purchase_price"`
	Profit        float64 `json:"profit"`
	LastModified  string  `json:"last_modified"`
	Public        bool    `json:"public"`
}
