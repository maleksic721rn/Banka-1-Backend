package dto

type UserRequest struct {
	UserId int64 `json:"userId"`
}

type Account struct {
	ID                    int64   `json:"id"`
	OwnerID               int64   `json:"ownerID"`
	AccountNumber         string  `json:"accountNumber"`
	Balance               float64 `json:"balance"`
	ReservedBalance       float64 `json:"reservedBalance"`
	Type                  string  `json:"type"`
	CurrencyType          string  `json:"currencyType"`
	Subtype               string  `json:"subtype"`
	CreatedDate           int64   `json:"createdDate"`
	ExpirationDate        int64   `json:"expirationDate"`
	DailyLimit            float64 `json:"dailyLimit"`
	MonthlyLimit          float64 `json:"monthlyLimit"`
	DailySpent            float64 `json:"dailySpent"`
	MonthlySpent          float64 `json:"monthlySpent"`
	Status                string  `json:"status"`
	EmployeeID            int64   `json:"employeeID"`
	MonthlyMaintenanceFee float64 `json:"monthlyMaintenanceFee"`
}

type UserAccountsResponse struct {
	Accounts []Account `json:"accounts"`
}

type OTCPremiumFeeDTO struct {
	SellerAccountId uint    `json:"sellerAccountId"`
	BuyerAccountId  uint    `json:"buyerAccountId"`
	Amount          float64 `json:"amount"`
}
