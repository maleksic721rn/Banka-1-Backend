package types

type OTCTransactionInitiationDTO struct {
	Uid             string  `json:"uid"`
	SellerAccountId uint    `json:"sellerAccountId"`
	BuyerAccountId  uint    `json:"buyerAccountId"`
	Amount          float64 `json:"amount"`
}

type OTCTransactionACKDTO struct {
	Uid     string `json:"uid"`
	Failure bool   `json:"failure"`
	Message string `json:"message"`
}
