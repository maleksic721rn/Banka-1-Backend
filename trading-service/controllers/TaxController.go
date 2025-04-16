package controllers

import (
	"log"
	"time"

	"banka1.com/db"
	"banka1.com/middlewares"
	"banka1.com/types"
	"github.com/gofiber/fiber/v2"
)

type TaxController struct {
}

func NewTaxController() *TaxController {
	return &TaxController{}
}

// GetTaxForAllUsers godoc
//
//	@Summary		Dohvatanje poslednjeg neplaćenog poreza za sve korisnike
//	@Description	Vraća listu najskorijih neplaćenih poreskih obaveza (za poslednji obračunati mesec/godinu) za sve korisnike. Za svakog korisnika proverava i da li je registrovan kao aktuar.
//	@Tags			Tax
//	@Produce		json
//	@Success		200	{object}	types.Response{data=[]types.TaxResponse}	"Lista poslednjih neplaćenih poreskih obaveza"
//	@Failure		400	{object}	types.Response								"Greška pri izvršavanju upita u bazi (kako je implementirano u kodu)"
//	@Failure		500	{object}	types.Response								"Greška pri čitanju rezultata iz baze"
//	@Router			/tax [get]
func (tc *TaxController) GetTaxForAllUsers(c *fiber.Ctx) error {
	rows, err := db.DB.Raw(`WITH max_created_at AS (SELECT user_id, MAX(created_at) AS c FROM tax GROUP BY user_id)
SELECT user_id, taxable_profit, tax_amount, is_paid, actuary.id IS NOT NULL
FROM tax LEFT JOIN actuary USING (user_id)
WHERE month_year = (SELECT MAX(month_year) FROM tax)
AND created_at = (SELECT c FROM max_created_at WHERE max_created_at.user_id = tax.user_id)
AND NOT is_paid;`).Rows()
	defer rows.Close()
	if err != nil {
		return c.Status(400).JSON(types.Response{
			Success: false,
			Error:   "Neuspeo zahtev: " + err.Error(),
		})
	}
	responses := make([]types.TaxResponse, 0)
	for rows.Next() {
		var response types.TaxResponse
		err := rows.Scan(&response.UserID, &response.TaxableProfit, &response.TaxAmount, &response.IsPaid, &response.IsActuary)
		if err != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Error:   "Greska prilikom citanja redova iz baze: " + err.Error(),
			})
		}
		responses = append(responses, response)
	}
	return c.JSON(types.Response{
		Success: true,
		Data:    responses,
	})
}

// RunTax godoc
//
//	@Summary		Pokretanje obračuna poreza
//	@Description	Endpoint namenjen za pokretanje procesa obračuna poreza za korisnike. Trenutno nije implementiran i uvek vraća grešku 500.
//	@Tags			Tax
//	@Produce		json
//	@Success		202	{object}	types.Response	"Zahtev za obračun poreza je primljen"
//	@Failure		500	{object}	types.Response	"Greska"
//	@Router			/tax/run [post]
func (tc *TaxController) RunTax(c *fiber.Ctx) error {
	return c.Status(500).JSON(types.Response{
		Success: false,
		Error:   "Nije implementirano.",
	})
}

// GetAggregatedTaxForUser godoc
//
//	@Summary		Dohvatanje agregiranih poreskih podataka za korisnika
//	@Description	Vraća sumu plaćenog poreza za tekuću godinu i sumu neplaćenog poreza za tekući mesec za specificiranog korisnika.
//	@Tags			Tax
//	@Produce		json
//	@Param			userID	path		int									true	"ID korisnika čiji se podaci traže"	example(123)
//	@Success		200		{object}	types.Response{data=types.AggregatedTaxResponse}	"Agregirani poreski podaci za korisnika"
//	@Failure		400		{object}	types.Response									"Neispravan ID korisnika (nije validan broj ili <= 0)"
//	@Failure		500		{object}	types.Response									"Interna greška servera pri dohvatanju podataka iz baze"
//	@Router			/tax/dashboard/{userID} [get]
func (tc *TaxController) GetAggregatedTaxForUser(c *fiber.Ctx) error {
	userID, err := c.ParamsInt("userID")
	if err != nil || userID <= 0 {
		return c.Status(400).JSON(types.Response{
			Success: false,
			Error:   "Neispravan userID parametar",
		})
	}

	year := time.Now().Format("2006")
	yearMonth := time.Now().Format("2006-01")

	var paid float64
	err = db.DB.Raw(`
		SELECT COALESCE(SUM(tax_amount), 0)
		FROM tax
		WHERE is_paid = 1 AND user_id = ? AND substr(month_year, 1, 4) = ?
	`, userID, year).Scan(&paid).Error

	if err != nil {
		log.Printf("Greška pri dohvatanju plaćenog poreza za user-a %d: %v", userID, err)
		return c.Status(500).JSON(types.Response{
			Success: false,
			Error:   "Greška pri čitanju podataka iz baze",
		})
	}

	var unpaid float64
	err = db.DB.Raw(`
		SELECT COALESCE(SUM(tax_amount), 0)
		FROM tax
		WHERE is_paid = 0 AND user_id = ? AND month_year = ?
	`, userID, yearMonth).Scan(&unpaid).Error

	if err != nil {
		log.Printf("Greška pri dohvatanju neplaćenog poreza za user-a %d: %v", userID, err)
		return c.Status(500).JSON(types.Response{
			Success: false,
			Error:   "Greška pri čitanju podataka iz baze",
		})
	}

	var isActuary bool
	db.DB.Raw(`
		SELECT COUNT(*) > 0
		FROM actuary
		WHERE user_id = ?
	`, userID).Scan(&isActuary)

	response := types.AggregatedTaxResponse{
		UserID:          uint(userID),
		PaidThisYear:    paid,
		UnpaidThisMonth: unpaid,
		IsActuary:       isActuary,
	}

	return c.JSON(types.Response{
		Success: true,
		Data:    response,
	})
}

func InitTaxRoutes(app *fiber.App) {
	taxController := NewTaxController()

	app.Get("/tax", middlewares.Auth, middlewares.DepartmentCheck("SUPERVISOR"), taxController.GetTaxForAllUsers)
	app.Post("/tax/run", middlewares.Auth, middlewares.DepartmentCheck("SUPERVISOR"), taxController.RunTax)
	app.Get("/tax/dashboard/:userID", middlewares.Auth, taxController.GetAggregatedTaxForUser)
}
