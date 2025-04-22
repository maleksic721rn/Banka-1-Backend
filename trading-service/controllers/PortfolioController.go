package controllers

import (
	"banka1.com/controllers/orders"
	"banka1.com/db"
	"banka1.com/middlewares"
	"banka1.com/types"
	"fmt"
	"github.com/gofiber/fiber/v2"
	"strconv"
)

type PortfolioController struct {
}

func NewPortfolioController() *PortfolioController { return &PortfolioController{} }

type UpdatePublicCountRequest struct {
	PortfolioID uint `json:"portfolio_id"`
	PublicCount *int `json:"public"`
}

// UpdatePublicCount godoc
//
//	@Summary		Ažuriranje broja javno oglašenih hartija
//	@Description	Menja broj hartija koje su označene kao javne u portfoliju korisnika.
//	@Tags			Portfolio
//	@Accept			json
//	@Produce		json
//	@Param			body	body	UpdatePublicCountRequest			true	"Podaci za ažuriranje"
//	@Success		200	{object}	types.Response{data=string}			"Uspešna izmena"
//	@Failure		400	{object}	types.Response						"Nedostaje user ID ili telo nije ispravno"
//	@Failure		500	{object}	types.Response						"Greška pri ažuriranju"
//	@Router			/securities/public-count [put]
func (sc *PortfolioController) UpdatePublicCount(c *fiber.Ctx) error {
	var req struct {
		PortfolioID uint `json:"portfolio_id"`
		PublicCount *int `json:"public"`
	}

	if err := c.BodyParser(&req); err != nil {
		return c.Status(400).JSON(types.Response{
			Success: false,
			Error:   "Invalid request body",
		})
	}

	if *req.PublicCount < 0 {
		return c.Status(400).JSON(types.Response{
			Success: false,
			Error:   "Public count cannot be negative",
		})
	}

	var portfolio types.Portfolio
	if err := db.DB.Preload("Security").First(&portfolio, req.PortfolioID).Error; err != nil {
		return c.Status(404).JSON(types.Response{
			Success: false,
			Error:   "Portfolio not found",
		})
	}

	if *req.PublicCount > portfolio.Quantity {
		return c.Status(400).JSON(types.Response{
			Success: false,
			Error:   "Public count cannot be greater than total amount",
		})
	}
	fmt.Printf("Portfolio: %+v\n", portfolio)
	if portfolio.Security.Type != "Stock" {
		return c.Status(400).JSON(types.Response{
			Success: false,
			Error:   "Hartija od vrednosti mora biti akcija",
		})
	}

	// Izmena u bazi
	if err := db.DB.Model(&portfolio).Update("public_count", *req.PublicCount).Error; err != nil {
		return c.Status(500).JSON(types.Response{
			Success: false,
			Error:   "Failed to update public count: " + err.Error(),
		})
	}

	return c.JSON(types.Response{
		Success: true,
		Data:    fmt.Sprintf("Updated public count to %d", *req.PublicCount),
	})
}

func (pc *PortfolioController) GetAllPortfolios(c *fiber.Ctx) error {
	var portfolios []types.Portfolio

	if err := db.DB.Preload("Security").Find(&portfolios).Error; err != nil {
		return c.Status(500).JSON(types.Response{
			Success: false,
			Error:   "Greška pri dohvatanju portfolija: " + err.Error(),
		})
	}

	return c.JSON(types.Response{
		Success: true,
		Data:    portfolios,
	})
}

// GetAvailableToSell godoc
//
//	@Summary		Dohvata broj dostupnih hartija koje korisnik može da proda
//	@Description	Računa količinu hartija koje korisnik može trenutno da proda, uzimajući u obzir javne hartije i već rezervisane SELL naloge.
//	@Tags			Portfolio
//	@Produce		json
//	@Param			user_id		query	int	true	"ID korisnika"
//	@Param			security_id	query	int	true	"ID hartije"
//	@Success		200	{object} types.Response{data=int} "Dostupna količina za prodaju"
//	@Failure		400	{object} types.Response "Nedostaju parametri ili greška u bazi"
//	@Router			/portfolio/available-to-sell [get]
func (pc *PortfolioController) GetAvailableToSell(c *fiber.Ctx) error {
	userID, err1 := strconv.Atoi(c.Query("user_id"))
	secID, err2 := strconv.Atoi(c.Query("security_id"))
	if err1 != nil || err2 != nil {
		return c.Status(400).JSON(types.Response{
			Success: false,
			Error:   "Nedostaje user_id ili security_id",
		})
	}

	_, available, err := orders.CanSell(uint(userID), uint(secID), 0)
	if err != nil {
		return c.Status(500).JSON(types.Response{
			Success: false,
			Error:   "Greška pri proveri dostupnosti hartija: " + err.Error(),
		})
	}

	return c.JSON(types.Response{
		Success: true,
		Data:    available,
	})
}

func InitPortfolioRoutes(app *fiber.App) {
	portfolioController := NewPortfolioController()

	app.Put("/securities/public-count", middlewares.Auth, portfolioController.UpdatePublicCount)
	app.Get("/portfolio/available-to-sell", portfolioController.GetAvailableToSell)
	app.Get("/portfolios", portfolioController.GetAllPortfolios)

}
