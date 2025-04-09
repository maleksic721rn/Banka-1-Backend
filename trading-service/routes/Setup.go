package routes

import (
	"banka1.com/controllers"
	"github.com/gofiber/fiber/v2"
)

func SetupRoutes(app *fiber.App) {
	controllers.InitActuaryRoutes(app)
	controllers.InitOrderRoutes(app)
	controllers.InitSecuritiesRoutes(app)
	controllers.InitExchangeRoutes(app)
	controllers.InitStockRoutes(app)
	controllers.InitForexRoutes(app)
	controllers.InitFutureRoutes(app)
	controllers.InitTaxRoutes(app)
	controllers.InitOptionsRoutes(app)
	controllers.InitPortfolioRoutes(app)
	controllers.InitOTCTradeRoutes(app)
	controllers.InitPortfolioRoutess(app)
}
