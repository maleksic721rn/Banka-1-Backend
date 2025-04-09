package broker

// Bitno je da je prvo slovo svakog polja veliko, da bi encoding/json mogao da ih vidi.
type CustomerResponse struct {
	Id int64

	FirstName string

	LastName string

	Username string

	BirthDate string

	Gender string

	Email string

	PhoneNumber string

	Address string

	Permissions []string
}
