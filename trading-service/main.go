package main

import (
	"banka1.com/controllers"
	fiberSwagger "github.com/swaggo/fiber-swagger"
	"os"
	//"strings"
	"time"

	"banka1.com/cron"

	// options "banka1.com/listings/options"
	"banka1.com/middlewares"

	"banka1.com/db"
	_ "banka1.com/docs"
	"banka1.com/exchanges"
	"banka1.com/listings/finhub"
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

	//routes.Setup(app, controllers.NewActuaryController())

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

	// GetAllExchanges godoc
	//	@Summary		Preuzimanje svih berzi
	//	@Description	Vraća listu svih berzi dostupnih u sistemu.
	//	@Tags			Exchanges
	//	@Produce		json
	//	@Success		200	{object}	types.Response{data=[]types.Exchange}	"Lista svih berzi"
	//	@Failure		500	{object}	types.Response							"Interna greška servera pri preuzimanju berzi"
	//	@Router			/exchanges [get]
	app.Get("/exchanges", func(c *fiber.Ctx) error {
		/*
			Function to get all the exchanges from the database
			Model:
				ID        uint   	UNIQUE
				Name      string
				Acronym   string
				MicCode   string 	UNIQUE
				Country   string
				Currency  string
				Timezone  string
				OpenTime  string
				CloseTime string
		*/
		var exchanges []types.Exchange

		if result := db.DB.Find(&exchanges); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch exchanges: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    exchanges,
			Error:   "",
		})
	})

	// GetExchangeByID godoc
	//	@Summary		Preuzimanje berze po ID-u
	//	@Description	Vraća detalje specifične berze na osnovu njenog internog ID-ja.
	//	@Tags			Exchanges
	//	@Produce		json
	//	@Param			id	path		int									true	"ID berze"
	//	@Success		200	{object}	types.Response{data=types.Exchange}	"Detalji tražene berze"
	//	@Failure		404	{object}	types.Response						"Berza sa datim ID-jem nije pronađena"
	//	@Router			/exchanges/{id} [get]
	app.Get("/exchanges/:id", func(c *fiber.Ctx) error {
		/*
			Get exchange by ID.
			ID is a unique identifier for the exchange.
			ID is assigned in the code and is not in the exchamges.csv data.
		*/
		id := c.Params("id")

		var exchange types.Exchange

		if result := db.DB.First(&exchange, id); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "ExchangeMic not found with ID: " + id,
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    exchange,
			Error:   "",
		})
	})

	// GetExchangeByMIC godoc
	//	@Summary		Preuzimanje berze po MIC kodu
	//	@Description	Vraća detalje specifične berze na osnovu njenog jedinstvenog MIC koda.
	//	@Tags			Exchanges
	//	@Produce		json
	//	@Param			micCode	path		string								true	"Market Identifier Code (MIC) berze"	example(XNAS)
	//	@Success		200		{object}	types.Response{data=types.Exchange}	"Detalji tražene berze"
	//	@Failure		404		{object}	types.Response						"Berza sa datim MIC kodom nije pronađena"
	//	@Router			/exchanges/mic/{micCode} [get]
	app.Get("/exchanges/mic/:micCode", func(c *fiber.Ctx) error {
		/*
			Get exchange by MIC code.
			Mic code is a Unique identifier for the exchange.
		*/
		micCode := c.Params("micCode")

		var exchange types.Exchange

		if result := db.DB.Where("mic_code = ?", micCode).First(&exchange); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "ExchangeMic not found with MIC code: " + micCode,
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    exchange,
			Error:   "",
		})
	})

	// GetExchangeByAcronym godoc
	//	@Summary		Preuzimanje berze po akronimu
	//	@Description	Vraća detalje specifične berze na osnovu njenog akronima. Napomena: Akronim ne mora biti jedinstven. Vraća prvu pronađenu.
	//	@Tags			Exchanges
	//	@Produce		json
	//	@Param			acronym	path		string								true	"Akronim berze"	example(NASDAQ)
	//	@Success		200		{object}	types.Response{data=types.Exchange}	"Detalji pronađene berze"
	//	@Failure		404		{object}	types.Response						"Berza sa datim akronimom nije pronađena"
	//	@Router			/exchanges/acronym/{acronym} [get]
	app.Get("/exchanges/acronym/:acronym", func(c *fiber.Ctx) error {
		/*
			Get exchange by acronym.
			Acronym is a short form of the exchange name.
			Acronym is usually unique BUT DOESNT HAVE TO BE!
		*/
		acronym := c.Params("acronym")

		var exchange types.Exchange

		if result := db.DB.Where("acronym = ?", acronym).First(&exchange); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "ExchangeMic not found with acronym: " + acronym,
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    exchange,
			Error:   "",
		})
	})

	// GetAllStocks godoc
	//	@Summary		Preuzimanje svih akcija
	//	@Description	Vraća listu svih listinga koji predstavljaju akcije.
	//	@Tags			Stocks
	//	@Produce		json
	//	@Success		200	{object}	types.Response{data=[]types.Listing}	"Lista svih akcija"
	//	@Failure		500	{object}	types.Response							"Interna greška servera pri preuzimanju akcija"
	//	@Router			/stocks [get]
	app.Get("/stocks", func(c *fiber.Ctx) error {
		var listings []types.Listing

		if result := db.DB.Preload("Exchange").Where("type = ?", "Stock").Find(&listings); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch stocks: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    listings,
			Error:   "",
		})
	})

	// GetStockByTicker godoc
	//	@Summary		Preuzimanje akcije po tikeru
	//	@Description	Vraća osnovne podatke (Listing) i detalje (Stock) za akciju specificiranu tikerom.
	//	@Tags			Stocks
	//	@Produce		json
	//	@Param			ticker	path		string																	true	"Tiker (simbol) akcije"	example(AAPL)
	//	@Success		200		{object}	types.Response{data=object{listing=types.Listing,details=types.Stock}}	"Detalji akcije"
	//	@Failure		404		{object}	types.Response															"Akcija sa datim tikerom nije pronađena"
	//	@Failure		500		{object}	types.Response															"Interna greška servera pri preuzimanju detalja akcije"
	//	@Router			/stocks/{ticker} [get]
	app.Get("/stocks/:ticker", func(c *fiber.Ctx) error {
		ticker := c.Params("ticker")

		var listing types.Listing
		if result := db.DB.Preload("Exchange").Where("ticker = ? AND type = ?", ticker, "Stock").First(&listing); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Stock not found with ticker: " + ticker,
			})
		}

		var stock types.Stock
		if result := db.DB.Preload("Listing.Exchange").Where("listing_id = ?", listing.ID).First(&stock); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch stock details: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data: map[string]interface{}{
				"listing": listing,
				"details": stock,
			},
			Error: "",
		})
	})

	// GetStockFirstHistory godoc
	//	@Summary		Prvi istorijski podatak za akciju
	//	@Description	Preuzima najstariji dostupan dnevni istorijski podatak za akciju.
	//	@Tags			Stocks
	//	@Produce		json
	//	@Param			ticker	path		string											true	"Tiker (simbol) akcije"	example(AAPL)
	//	@Success		200		{object}	types.Response{data=finhub.HistoricalPriceData}	"Najstariji istorijski podatak"
	//	@Failure		404		{object}	types.Response									"Akcija sa datim tikerom nije pronađena"
	//	@Failure		500		{object}	types.Response									"Greška pri preuzimanju istorijskih podataka"
	//	@Router			/stocks/{ticker}/history/first [get]
	app.Get("/stocks/:ticker/history/first", func(c *fiber.Ctx) error {
		ticker := c.Params("ticker")

		// Find the listing first
		var listing types.Listing
		if result := db.DB.Where("ticker = ? AND type = ?", ticker, "Stock").First(&listing); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Stock not found with ticker: " + ticker,
			})
		}

		history, err := finhub.GetHistoricalPriceFirst(ticker, listing.Subtype)
		if err != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch historical price data: " + err.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    history,
			Error:   "",
		})
	})

	// GetStockHistoryByDate godoc
	//	@Summary		Istorijski podatak za akciju za određeni datum
	//	@Description	Preuzima dnevni istorijski podatak za akciju za određeni datum.
	//	@Tags			Stocks
	//	@Produce		json
	//	@Param			ticker	path		string											true	"Tiker (simbol) akcije"			example(AAPL)
	//	@Param			date	path		string											true	"Datum u formatu YYYY-MM-DD"	example(2023-10-26)	Format(date)
	//	@Success		200		{object}	types.Response{data=finhub.HistoricalPriceData}	"Istorijski podatak za dati datum"
	//	@Failure		400		{object}	types.Response									"Neispravan format datuma"
	//	@Failure		404		{object}	types.Response									"Akcija sa datim tikerom nije pronađena"
	//	@Failure		500		{object}	types.Response									"Greška pri preuzimanju istorijskih podataka"
	//	@Router			/stocks/{ticker}/history/{date} [get]
	app.Get("/stocks/:ticker/history/:date", func(c *fiber.Ctx) error {
		ticker := c.Params("ticker")
		date := c.Params("date")

		// Find the listing first
		var listing types.Listing
		if result := db.DB.Where("ticker = ? AND type = ?", ticker, "Stock").First(&listing); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Stock not found with ticker: " + ticker,
			})
		}

		// Parse date parameter
		dateTime, err := time.Parse("2006-01-02", date)
		if err != nil {
			return c.Status(400).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Invalid date format. Use YYYY-MM-DD",
			})
		}

		history, err := finhub.GetHistoricalPriceDate(ticker, listing.Subtype, dateTime)
		if err != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch historical price data: " + err.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    history,
			Error:   "",
		})
	})

	// GetStockHistoryRange godoc
	//	@Summary		Istorijski podaci za akciju u vremenskom opsegu
	//	@Description	Preuzima dnevne istorijske podatke za akciju u vremenskom opsegu.
	//	@Tags			Stocks
	//	@Produce		json
	//	@Param			ticker		path		string												true	"Tiker (simbol) akcije"										example(AAPL)
	//	@Param			startDate	query		string												false	"Početni datum (YYYY-MM-DD). Podrazumevano pre 30 dana."	example(2023-09-27)	Format(date)
	//	@Param			endDate		query		string												false	"Krajnji datum (YYYY-MM-DD). Podrazumevano današnji dan."	example(2023-10-27)	Format(date)
	//	@Success		200			{object}	types.Response{data=[]types.ListingDailyPriceInfo}	"Lista istorijskih podataka"
	//	@Failure		400			{object}	types.Response										"Neispravan format datuma"
	//	@Failure		404			{object}	types.Response										"Akcija sa datim tikerom nije pronađena"
	//	@Failure		500			{object}	types.Response										"Greška pri preuzimanju istorijskih podataka iz baze"
	//	@Router			/stocks/{ticker}/history [get]
	app.Get("/stocks/:ticker/history", func(c *fiber.Ctx) error {
		ticker := c.Params("ticker")
		// Parse date range parameters
		startDateStr := c.Query("startDate", "")
		endDateStr := c.Query("endDate", "")

		var listing types.Listing
		if result := db.DB.Where("ticker = ? AND type = ?", ticker, "Stock").First(&listing); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Stock not found with ticker: " + ticker,
			})
		}

		var startDate, endDate time.Time
		var err error

		if startDateStr != "" {
			startDate, err = time.Parse("2006-01-02", startDateStr)
			if err != nil {
				return c.Status(400).JSON(types.Response{
					Success: false,
					Data:    nil,
					Error:   "Invalid startDate format. Use YYYY-MM-DD",
				})
			}
		} else {
			// Default to 30 days ago
			startDate = time.Now().AddDate(0, 0, -30)
		}

		if endDateStr != "" {
			endDate, err = time.Parse("2006-01-02", endDateStr)
			if err != nil {
				return c.Status(400).JSON(types.Response{
					Success: false,
					Data:    nil,
					Error:   "Invalid endDate format. Use YYYY-MM-DD",
				})
			}
		} else {
			// Default to today
			endDate = time.Now()
		}

		// Fetch historical price data
		var history []types.ListingDailyPriceInfo
		if result := db.DB.Where("listing_id = ? AND date BETWEEN ? AND ?",
			listing.ID, startDate, endDate).Order("date").Find(&history); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch historical price data: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    history,
			Error:   "",
		})
	})

	// GetAllForex godoc
	//	@Summary		Preuzimanje svih forex parova
	//	@Description	Vraća listu svih listinga koji predstavljaju forex valutne parove.
	//	@Tags			Forex
	//	@Produce		json
	//	@Success		200	{object}	types.Response{data=[]types.Listing}	"Lista svih forex parova"
	//	@Failure		500	{object}	types.Response							"Interna greška servera pri preuzimanju forex parova"
	//	@Router			/forex [get]
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

	app.Get("/forex", func(c *fiber.Ctx) error {
		var listings []types.Listing

		if result := db.DB.Preload("Exchange").Where("type = ?", "Forex").Find(&listings); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch stocks: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    listings,
			Error:   "",
		})
	})

	// GetForexByPair godoc
	//	@Summary		Preuzimanje forex para po valutama
	//	@Description	Vraća osnovne podatke (Listing) i detalje (ForexPair) za forex par specificiran osnovnom i kvotnom valutom.
	//	@Tags			Forex
	//	@Produce		json
	//	@Param			base	path		string																		true	"Osnovna valuta (ISO kod)"	example(EUR)
	//	@Param			quote	path		string																		true	"Kvotna valuta (ISO kod)"	example(USD)
	//	@Success		200		{object}	types.Response{data=object{listing=types.Listing,details=types.ForexPair}}	"Detalji forex para"
	//	@Failure		404		{object}	types.Response																"Forex par nije pronađen"
	//	@Failure		500		{object}	types.Response																"Interna greška servera pri preuzimanju detalja forex para"
	//	@Router			/forex/{base}/{quote} [get]
	app.Get("/forex/:base/:quote", func(c *fiber.Ctx) error {

		base := c.Params("base")
		quote := c.Params("quote")
		ticker := base + "/" + quote

		var listing types.Listing
		if result := db.DB.Preload("Exchange").Where("ticker = ? AND type = ?", ticker, "Forex").First(&listing); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Forex pair not found with ticker: " + ticker,
			})
		}

		var forexPair types.ForexPair
		if result := db.DB.Preload("Listing.Exchange").Where("listing_id = ?", listing.ID).First(&forexPair); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch stock details: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data: map[string]interface{}{
				"listing": listing,
				"details": forexPair,
			},
			Error: "",
		})
	})

	// GetForexHistoryRange godoc
	//	@Summary		Istorijski podaci za forex par u vremenskom opsegu
	//	@Description	Preuzima dnevne istorijske podatke za forex par u vremenskom opsegu.
	//	@Tags			Forex
	//	@Produce		json
	//	@Param			base		path		string												true	"Osnovna valuta"											example(EUR)
	//	@Param			quote		path		string												true	"Kvotna valuta"												example(USD)
	//	@Param			startDate	query		string												false	"Početni datum (YYYY-MM-DD). Podrazumevano pre 30 dana."	example(2023-09-27)	Format(date)
	//	@Param			endDate		query		string												false	"Krajnji datum (YYYY-MM-DD). Podrazumevano današnji dan."	example(2023-10-27)	Format(date)
	//	@Success		200			{object}	types.Response{data=[]types.ListingDailyPriceInfo}	"Lista istorijskih podataka"
	//	@Failure		400			{object}	types.Response										"Neispravan format datuma"
	//	@Failure		404			{object}	types.Response										"Forex par nije pronađen"
	//	@Failure		500			{object}	types.Response										"Greška pri preuzimanju istorijskih podataka iz baze"
	//	@Router			/forex/{base}/{quote}/history [get]
	app.Get("/forex/:base/:quote/history", func(c *fiber.Ctx) error {
		base := c.Params("base")
		quote := c.Params("quote")
		ticker := base + "/" + quote
		startDateStr := c.Query("startDate", "")
		endDateStr := c.Query("endDate", "")
		var listing types.Listing
		if result := db.DB.Where("ticker = ? AND type = ?", ticker, "Forex").First(&listing); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Stock not found with ticker: " + ticker,
			})
		}

		var startDate, endDate time.Time
		var err error

		if startDateStr != "" {
			startDate, err = time.Parse("2006-01-02", startDateStr)
			if err != nil {
				return c.Status(400).JSON(types.Response{
					Success: false,
					Data:    nil,
					Error:   "Invalid startDate format. Use YYYY-MM-DD",
				})
			}
		} else {
			// Default to 30 days ago
			startDate = time.Now().AddDate(0, 0, -30)
		}

		if endDateStr != "" {
			endDate, err = time.Parse("2006-01-02", endDateStr)
			if err != nil {
				return c.Status(400).JSON(types.Response{
					Success: false,
					Data:    nil,
					Error:   "Invalid endDate format. Use YYYY-MM-DD",
				})
			}
		} else {
			// Default to today
			endDate = time.Now()
		}

		// Fetch historical price data
		var history []types.ListingDailyPriceInfo
		if result := db.DB.Where("listing_id = ? AND date BETWEEN ? AND ?",
			listing.ID, startDate, endDate).Order("date").Find(&history); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch historical price data: " + result.Error.Error(),
			})
		}
		return c.JSON(types.Response{
			Success: true,
			Data:    history,
			Error:   "",
		})
	})

	// GetAllFutures godoc
	//	@Summary		Preuzimanje svih future-a
	//	@Description	Vraća listu svih listinga koji predstavljaju future ugovore.
	//	@Tags			Futures
	//	@Produce		json
	//	@Success		200	{object}	types.Response{data=[]types.Listing}	"Lista svih future-a"
	//	@Failure		500	{object}	types.Response							"Interna greška servera pri preuzimanju future-a"
	//	@Router			/future [get]
	app.Get("/future", func(c *fiber.Ctx) error {
		var listings []types.Listing

		if result := db.DB.Preload("Exchange").Where("type = ?", "Future").Find(&listings); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch stocks: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    listings,
			Error:   "",
		})
	})

	// GetFutureByTicker godoc
	//	@Summary		Preuzimanje future-a po tikeru
	//	@Description	Vraća osnovne podatke (Listing) i detalje (FuturesContract) za future specificiran tikerom.
	//	@Tags			Futures
	//	@Produce		json
	//	@Param			ticker	path		string																				true	"Tiker (simbol) future-a"	example(ESZ3)
	//	@Success		200		{object}	types.Response{data=object{listing=types.Listing,details=types.FuturesContract}}	"Detalji future-a"
	//	@Failure		404		{object}	types.Response																		"Future sa datim tikerom nije pronađen"
	//	@Failure		500		{object}	types.Response																		"Interna greška servera pri preuzimanju detalja future-a"
	//	@Router			/future/{ticker} [get]
	app.Get("/future/:ticker", func(c *fiber.Ctx) error {
		ticker := c.Params("ticker")

		var listing types.Listing
		if result := db.DB.Preload("Exchange").Where("ticker = ? AND type = ?", ticker, "Future").First(&listing); result.Error != nil {
			return c.Status(404).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Stock not found with ticker: " + ticker,
			})
		}

		var future types.FuturesContract
		if result := db.DB.Preload("Listing.Exchange").Where("listing_id = ?", listing.ID).First(&future); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch future details: " + result.Error.Error(),
			})
		}

		return c.JSON(types.Response{
			Success: true,
			Data: map[string]interface{}{
				"listing": listing,
				"details": future,
			},
			Error: "",
		})
	})

	// GetAllSecuritiesAvailable godoc
	//	@Summary		Preuzimanje svih dostupnih hartija od vrednosti
	//	@Description	Vraća listu svih dostupnih hartija od vrednosti.
	//	@Tags			Securities
	//	@Produce		json
	//	@Success		200	{object}	types.Response{data=[]types.Security}	"Lista svih hartija od vrednosti"
	//	@Failure		500	{object}	types.Response							"Interna greška servera pri preuzimanju ili konverziji hartija od vrednosti"
	//	@Router			/securities/available [get]
	app.Get("/securities/available", getSecurities())

	// GetAllSecurities godoc
	//	@Summary		Preuzimanje svih hartija od vrednosti (Alias)
	//	@Description	Vraća listu svih dostupnih hartija od vrednosti.
	//	@Tags			Securities
	//	@Produce		json
	//	@Success		200	{object}	types.Response{data=[]types.Security}	"Lista svih hartija od vrednosti"
	//	@Failure		500	{object}	types.Response							"Interna greška servera pri preuzimanju ili konverziji hartija od vrednosti"
	//	@Router			/securities [get]
	app.Get("/securities", getSecurities())

	app.Post("/actuaries", controllers.NewActuaryController().CreateActuary)
	app.Get("/actuaries", controllers.NewActuaryController().GetAllActuaries)
	app.Put("/actuaries/:ID", controllers.NewActuaryController().ChangeAgentLimits)
	app.Get("/actuaries/filter", controllers.NewActuaryController().FilterActuaries)

	orders.InitRoutes(app)
	tax.InitRoutes(app)

	app.Get("/swagger/*", fiberSwagger.WrapHandler)

	port := os.Getenv("LISTEN_PATH")
	log.Printf("Swagger UI available at http://localhost%s/swagger/index.html", port)
	log.Fatal(app.Listen(port))
}

func getSecurities() func(c *fiber.Ctx) error {
	return func(c *fiber.Ctx) error {
		var listings []types.Listing
		if result := db.DB.Preload("Exchange").Find(&listings); result.Error != nil {
			return c.Status(500).JSON(types.Response{
				Success: false,
				Data:    nil,
				Error:   "Failed to fetch securities: " + result.Error.Error(),
			})
		}
		var securities []types.Security
		for _, listing := range listings {
			security, err := listingToSecurity(&listing)
			if err != nil || security == nil {
				return c.Status(500).JSON(types.Response{
					Success: false,
					Data:    nil,
					Error:   "Failed to convert listing to security: " + err.Error(),
				})
			}
			securities = append(securities, *security)
		}

		return c.JSON(types.Response{
			Success: true,
			Data:    securities,
			Error:   "",
		})
	}
}

func listingToSecurity(l *types.Listing) (*types.Security, error) {
	var security types.Security
	switch l.Type {
	case "Stock":
		{
			security = types.Security{
				ID:        l.ID,
				Ticker:    l.Ticker,
				Name:      l.Name,
				Type:      l.Type,
				Exchange:  l.Exchange.Name,
				LastPrice: float64(l.Price),
				AskPrice:  float64(l.Ask),
				BidPrice:  float64(l.Bid),
				Volume:    int64(l.ContractSize * 10),
			}
		}
	case "Forex":
		{
			security = types.Security{
				ID:        l.ID,
				Ticker:    l.Ticker,
				Name:      l.Name,
				Type:      l.Type,
				Exchange:  l.Exchange.Name,
				LastPrice: float64(l.Price),
				AskPrice:  float64(l.Ask),
				BidPrice:  float64(l.Bid),
				Volume:    int64(l.ContractSize * 10),
			}
		}
	case "Future":
		{
			var future types.FuturesContract
			if result := db.DB.Where("listing_id = ?", l.ID).First(&future); result.Error != nil {
				return nil, result.Error
			}
			settlementDate := future.SettlementDate.Format("2006-01-02")
			security = types.Security{
				ID:             l.ID,
				Ticker:         l.Ticker,
				Name:           l.Name,
				Type:           l.Type,
				Exchange:       l.Exchange.Name,
				LastPrice:      float64(l.Price),
				AskPrice:       float64(l.Ask),
				BidPrice:       float64(l.Bid),
				Volume:         int64(l.ContractSize * 10),
				SettlementDate: &settlementDate,
			}
		}
	case "Option":
		{
			var option types.Option
			if result := db.DB.Where("listing_id = ?", l.ID).First(&option); result.Error != nil {
				return nil, result.Error
			}
			security = types.Security{
				ID:             l.ID,
				Ticker:         l.Ticker,
				Name:           l.Name,
				Type:           l.Type,
				Exchange:       l.Exchange.Name,
				LastPrice:      float64(l.Price),
				AskPrice:       float64(l.Ask),
				BidPrice:       float64(l.Bid),
				Volume:         int64(l.ContractSize * 10),
				StrikePrice:    &option.StrikePrice,
				OptionType:     &option.OptionType,
				SettlementDate: nil,
			}

		}

	}
	return &security, nil
}
