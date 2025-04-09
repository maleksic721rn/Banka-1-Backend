package controllers

import (
	"banka1.com/db"
	"banka1.com/types"
	"github.com/gofiber/fiber/v2"
)

type OptionsController struct {
}

func NewOptionsController() *OptionsController {
	return &OptionsController{}
}

// GetOptionsByTicker godoc
//
//	@Summary		Preuzimanje opcija po tačnom osnovnom tikeru
//	@Description	Vraća listu osnovnih podataka (Listing) i detalje (Option) za sve opcije koje imaju specificirani tačan osnovni tiker (npr. AAPL).
//	@Tags			Options
//	@Produce		json
//	@Param			ticker	path		string																		true	"Tačan osnovni tiker (simbol) instrumenta na koji se opcija odnosi"	example(AAPL)
//	@Success		200		{object}	types.Response{data=object{listing=[]types.Listing,details=[]types.Option}}	"Lista opcija za dati tiker"
//	@Failure		404		{object}	types.Response																"Opcije sa datim tikerom nisu pronađene"
//	@Failure		500		{object}	types.Response																"Interna greška servera pri preuzimanju detalja opcije"
//	@Router			/options/ticker/{ticker} [get]
func (oc *OptionsController) GetOptionsByTicker(c *fiber.Ctx) error {
	var listings []types.Listing

	ticker := c.Params("ticker")
	if result := db.DB.Preload("Exchange").Where("ticker = ? AND type = ?", ticker, "Option").Find(&listings); result.Error != nil {
		return c.Status(404).JSON(types.Response{
			Success: false,
			Data:    nil,
			Error:   "Options not found with ticker: " + ticker,
		})
	}

	var options []types.Option
	for _, listing := range listings {
		var option types.Option
		if result := db.DB.Preload("Listing.Exchange").Where("listing_id = ?", listing.ID).First(&option); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch option details: " + result.Error.Error(),
			})
		}
		options = append(options, option)
	}

	return c.JSON(types.Response{
		Success: true,
		Data: map[string]interface{}{
			"listing": listings,
			"details": options,
		},
		Error: "",
	})
}

// GetOptionsBySymbolPrefix godoc
//
//	@Summary		Preuzimanje opcija po prefiksu simbola opcije
//	@Description	Vraća listu osnovnih podataka (Listing) i detalje (Option) za sve opcije čiji tiker (simbol same opcije u `listings` tabeli) počinje specificiranim prefiksom (npr. "AAPL25" za sve AAPL opcije koje ističu 2025). Koristi LIKE pretragu.
//	@Tags			Options
//	@Produce		json
//	@Param			symbol	path		string																		true	"Prefiks tikera (simbola) opcije"	example(AAPL251219C)
//	@Success		200		{object}	types.Response{data=object{listing=[]types.Listing,details=[]types.Option}}	"Lista opcija za dati prefiks simbola"
//	@Failure		404		{object}	types.Response																"Opcije čiji simbol počinje datim prefiksom nisu pronađene"
//	@Failure		500		{object}	types.Response																"Interna greška servera pri preuzimanju detalja opcije"
//	@Router			/options/symbol/{symbol} [get]
func (oc *OptionsController) GetOptionsBySymbolPrefix(c *fiber.Ctx) error {
	symbol := c.Params("symbol")

	var listings []types.Listing
	if result := db.DB.Preload("Exchange").Where("ticker LIKE ? AND type = ?", symbol+"%", "Option").Find(&listings); result.Error != nil {
		return c.Status(404).JSON(types.Response{
			Success: false,
			Data:    nil,
			Error:   "Options not found with symbol: " + symbol,
		})
	}

	var options []types.Option
	for _, listing := range listings {
		var opt types.Option
		if result := db.DB.Where("listing_id = ?", listing.ID).First(&opt); result.Error != nil {
			continue // preskoči ako ne postoji option (kao kod futures)
		}
		options = append(options, opt)
	}

	return c.JSON(types.Response{
		Success: true,
		Data: map[string]interface{}{
			"listing": listings,
			"details": options,
		},
	})
}

func InitOptionsRoutes(app *fiber.App) {
	oc := NewOptionsController()

	app.Get("/options/ticker/:ticker", oc.GetOptionsByTicker)
	app.Get("/options/symbol/:symbol", oc.GetOptionsBySymbolPrefix)
}
