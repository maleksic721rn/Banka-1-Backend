package main

import (
	"banka1.com/controllers"
	fiberSwagger "github.com/swaggo/fiber-swagger"
	"os"

	"banka1.com/cron"

	// options "banka1.com/listings/options"
	"banka1.com/middlewares"

	"banka1.com/db"
	_ "banka1.com/docs"
	"banka1.com/exchanges"
	"banka1.com/listings/forex"
	"banka1.com/listings/futures"
	"banka1.com/listings/stocks"
	"banka1.com/orders"
	"banka1.com/tax"
	"banka1.com/types"
	"github.com/gofiber/fiber/v2"
	"github.com/joho/godotenv"

	"log"
)

//	@title			Trading Service
//	@version		1.0
//	@description	Trading Service API

//	@host		localhost:3000
//	@BasePath	/

// @securityDefinitions.apikey	BearerAuth
// @in							header
// @name						Authorization
// @description				Unesite token. Primer: "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
func main() {

	err := godotenv.Load()
	if err != nil {
		panic("Error loading .env file")
	}

	db.Init()
	cron.StartScheduler()

	err = exchanges.LoadDefaultExchanges()
	if err != nil {
		log.Printf("Warning: Failed to load exchanges: %v", err)
	}

	func() {
		log.Println("Starting to load default stocks...")
		stocks.LoadDefaultStocks()
		log.Println("Finished loading default stocks")
	}()

	func() {
		log.Println("Starting to load default forex pairs...")
		forex.LoadDefaultForexPairs()
		log.Println("Finished loading default forex pairs")
	}()

	func() {
		log.Println("Starting to load default futures...")
		err = futures.LoadDefaultFutures()
		if err != nil {
			log.Printf("Warning: Failed to load futures: %v", err)
		}
		log.Println("Finished loading default futures")
	}()

	// func() {
	// 	log.Println("Starting to load default options...")
	// 	err = options.LoadAllOptions()
	// 	if err != nil {
	// 		log.Printf("Warning: Failed to load options: %v", err)
	// 	}
	// 	log.Println("Finished loading default options")
	// }()

	app := fiber.New()

	app.Use(func(c *fiber.Ctx) error {
		c.Set("Access-Control-Allow-Origin", "*")
		c.Set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE")
		return c.Next()
	})

	app.Get("/", middlewares.Auth, middlewares.DepartmentCheck("AGENT"), func(c *fiber.Ctx) error {
		response := types.Response{
			Success: true,
			Data:    "Hello, World!",
			Error:   "",
		}
		return c.JSON(response)
	})

	app.Get("/health", func(c *fiber.Ctx) error {
		return c.SendStatus(200)
	})

	// GetAllSecuritiesAvailable godoc
	//	@Summary		Preuzimanje svih dostupnih hartija od vrednosti
	//	@Description	Vraća listu svih dostupnih hartija od vrednosti.
	//	@Tags			Securities
	//	@Produce		json
	//	@Success		200	{object}	types.Response{data=[]types.Security}	"Lista svih hartija od vrednosti"
	//	@Failure		500	{object}	types.Response							"Interna greška servera pri preuzimanju ili konverziji hartija od vrednosti"
	//	@Router			/securities/available [get]
	//app.Get("/securities/available", getSecurities())

	app.Get("/options/ticker/:ticker", func(c *fiber.Ctx) error {
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
	})

	app.Get("/options/symbol/:symbol", func(c *fiber.Ctx) error {
		var listings []types.Listing
		symbol := c.Params("symbol")
		if result := db.DB.Preload("Exchange").Where("ticker LIKE ? AND type = ?", symbol+"%", "Option").Find(&listings); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Options not found with symbol: " + symbol,
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
		})
	})

	controllers.InitOrderRoutes(app)
	controllers.InitActuaryRoutes(app)
	controllers.InitSecuritiesController(app)
	controllers.InitFutureController(app)
	controllers.InitForexController(app)
	controllers.InitStockController(app)
	controllers.InitExchangeController(app)

	app.Get("/swagger/*", fiberSwagger.WrapHandler)

	port := os.Getenv("LISTEN_PATH")
	log.Printf("Swagger UI available at http://localhost%s/swagger/index.html", port)
	log.Fatal(app.Listen(port))
}
