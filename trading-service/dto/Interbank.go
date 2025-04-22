package dto

type InterbankOtcOfferDTO struct {
	Stock          StockDescription `json:"stock"`
	SettlementDate string           `json:"settlementDate"`
	PricePerUnit   MonetaryValue    `json:"pricePerUnit"`
	Premium        MonetaryValue    `json:"premium"`
	BuyerID        ForeignBankId    `json:"buyerId"`
	SellerID       ForeignBankId    `json:"sellerId"`
	Amount         int              `json:"amount"`
	LastModifiedBy ForeignBankId    `json:"lastModifiedBy"`
}

type ForeignBankId struct {
	RoutingNumber int    `json:"routingNumber"`
	ID            string `json:"id"`
}

type StockDescription struct {
	Ticker string `json:"ticker"`
}

type PublicStock struct {
	Stock   StockDescription   `json:"stock"`
	Sellers []SellerStockEntry `json:"sellers"`
}

type SellerStockEntry struct {
	Seller ForeignBankId `json:"seller"`
	Amount int           `json:"amount"`
}

type MonetaryValue struct {
	Currency string  `json:"currency"`
	Amount   float64 `json:"amount"`
}

type PublicStocksResponse []PublicStock

type OtcNegotiation struct {
	Stock          StockDescription `json:"stock"`
	SettlementDate string           `json:"settlementDate"`
	PricePerUnit   MonetaryValue    `json:"pricePerUnit"`
	Premium        MonetaryValue    `json:"premium"`
	BuyerID        ForeignBankId    `json:"buyerId"`
	SellerID       ForeignBankId    `json:"sellerId"`
	Amount         int              `json:"amount"`
	LastModifiedBy ForeignBankId    `json:"lastModifiedBy"`
	IsOngoing      bool             `json:"isOngoing"`
}
