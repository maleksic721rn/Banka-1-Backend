package dto

type SecurityProfit struct {
	SecurityID uint    `json:"security_id"`
	Ticker     string  `json:"ticker"`
	Profit     float64 `json:"profit"`
}

type RealizedProfitResponse struct {
	UserID      uint             `json:"user_id"`
	TotalProfit float64          `json:"total_profit"`
	PerSecurity []SecurityProfit `json:"per_security"`
}
