package controllers

import (
	//"banka1.com/broker"
	"banka1.com/db"
	"banka1.com/dto"
	"banka1.com/middlewares"
	"banka1.com/types"
	"bytes"
	"encoding/json"
	"fmt"
	"github.com/go-playground/validator/v10"
	"github.com/gofiber/fiber/v2"
	"io"
	"net/http"
	"os"
	"strconv"
	"strings"
	"time"
)

type UpdateOTCTradeRequest struct {
	Quantity       int     `json:"quantity" validate:"required,gt=0"`
	PricePerUnit   float64 `json:"price_per_unit" validate:"required,gt=0"`
	Premium        float64 `json:"premium" validate:"required,gte=0"`
	SettlementDate string  `json:"settlement_date" validate:"required"`
}

type OTCTradeController struct {
	validator *validator.Validate
}

func NewOTCTradeController() *OTCTradeController {
	return &OTCTradeController{
		validator: validator.New(),
	}
}

type CreateOTCTradeRequest struct {
	OwnerID        string  `json:"ownerId" validate:"required"`
	PortfolioID    *uint   `json:"portfolioId,omitempty"`
	Ticker         *string `json:"ticker,omitempty"`
	Quantity       int     `json:"quantity"     validate:"required,gt=0"`
	PricePerUnit   float64 `json:"pricePerUnit" validate:"required,gt=0"`
	Premium        float64 `json:"premium"      validate:"required,gte=0"`
	SettlementDate string  `json:"settlementDate" validate:"required"`
}

func (c *OTCTradeController) CreateOTCTrade(ctx *fiber.Ctx) error {
	var req CreateOTCTradeRequest
	if err := ctx.BodyParser(&req); err != nil {
		return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{false, "", "Nevalidan JSON format"})
	}
	if err := c.validator.Struct(req); err != nil {
		return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{false, "", err.Error()})
	}
	settlementDate, err := time.Parse("2006-01-02", req.SettlementDate)
	if err != nil {
		return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{false, "", "Nevalidan format datuma, očekivano YYYY-MM-DD"})
	}
	localUserID := uint(ctx.Locals("user_id").(float64))
	localUserIDStr := strconv.FormatUint(uint64(localUserID), 10)

	if req.PortfolioID != nil {
		var portfolio types.Portfolio
		if err := db.DB.Preload("Security").First(&portfolio, *req.PortfolioID).Error; err != nil {
			return ctx.Status(404).JSON(types.Response{false, "", "Portfolio nije pronađen"})
		}
		if portfolio.UserID == localUserID {
			return ctx.Status(403).JSON(types.Response{false, "", "Ne možete praviti ponudu za svoje akcije"})
		}
		if portfolio.PublicCount < req.Quantity {
			return ctx.Status(400).JSON(types.Response{false, "", "Nedovoljno javno dostupnih akcija"})
		}
		trade := types.OTCTrade{
			PortfolioID:   req.PortfolioID,
			SecurityID:    &portfolio.SecurityID,
			LocalSellerID: &portfolio.UserID,
			LocalBuyerID:  &localUserID,
			Ticker:        portfolio.Security.Ticker,
			Quantity:      req.Quantity,
			PricePerUnit:  req.PricePerUnit,
			Premium:       req.Premium,
			SettlementAt:  settlementDate,
			ModifiedBy:    localUserIDStr,
			Status:        "pending",
		}
		if err := db.DB.Create(&trade).Error; err != nil {
			return ctx.Status(500).JSON(types.Response{false, "", "Greška pri čuvanju ponude"})
		}
		return ctx.Status(201).JSON(types.Response{true, fmt.Sprintf("Interna ponuda kreirana: %d", trade.ID), ""})
	}

	if req.Ticker == nil {
		return ctx.Status(400).JSON(types.Response{false, "", "Ticker je obavezan za međubankarsku ponudu"})
	}
	prefix := req.OwnerID[:3]
	foreignID := req.OwnerID[3:]
	routingNum, err := strconv.Atoi(prefix)
	if err != nil {
		return ctx.Status(400).JSON(types.Response{false, "", "Neispravan ownerId format"})
	}

	ibReq := CreateInterbankOTCOfferRequest{
		Ticker:         *req.Ticker,
		Quantity:       req.Quantity,
		PricePerUnit:   req.PricePerUnit,
		Premium:        req.Premium,
		SettlementDate: req.SettlementDate,
		SellerRouting:  routingNum,
		SellerID:       foreignID,
	}

	if err := c.validator.Struct(ibReq); err != nil {
		return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{false, "", err.Error()})
	}

	const myRouting = 111
	offerDTO := dto.InterbankOtcOfferDTO{
		Stock:          dto.StockDescription{Ticker: ibReq.Ticker},
		SettlementDate: settlementDate.Format(time.RFC3339),
		PricePerUnit:   dto.MonetaryValue{Currency: "USD", Amount: ibReq.PricePerUnit},
		Premium:        dto.MonetaryValue{Currency: "USD", Amount: ibReq.Premium},
		Amount:         ibReq.Quantity,
		BuyerID:        dto.ForeignBankId{RoutingNumber: myRouting, ID: localUserIDStr},
		SellerID:       dto.ForeignBankId{RoutingNumber: ibReq.SellerRouting, ID: ibReq.SellerID},
		LastModifiedBy: dto.ForeignBankId{RoutingNumber: myRouting, ID: localUserIDStr},
	}

	url := fmt.Sprintf("%s/negotiations", os.Getenv("BANK4_BASE_URL"))
	bodyBytes, err := json.Marshal(offerDTO)
	if err != nil {
		return ctx.Status(500).JSON(types.Response{false, "", "Greška pri serializaciji zahteva"})
	}

	bodyReader := bytes.NewReader(bodyBytes)

	httpReq, err := http.NewRequest("POST", url, bodyReader)
	if err != nil {
		return ctx.Status(500).JSON(types.Response{false, "", "Greška pri kreiranju HTTP zahteva"})
	}
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("X-Api-Key", os.Getenv("BANK4_API_KEY"))

	resp, err := http.DefaultClient.Do(httpReq)
	if err != nil {
		return ctx.Status(502).JSON(types.Response{false, "", "Greška pri komunikaciji sa Bankom 4"})
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		body, _ := io.ReadAll(resp.Body)
		return ctx.Status(resp.StatusCode).JSON(types.Response{false, fmt.Sprintf("Bank4: %s", string(body)), ""})
	}

	var fbid dto.ForeignBankId
	if err := json.NewDecoder(resp.Body).Decode(&fbid); err != nil {
		return ctx.Status(500).JSON(types.Response{false, "", "Neuspešno parsiranje odgovora Banke 4"})
	}

	trade := types.OTCTrade{
		RemoteRoutingNumber: &fbid.RoutingNumber,
		RemoteNegotiationID: &fbid.ID,
		RemoteSellerID:      &ibReq.SellerID,
		RemoteBuyerID:       &localUserIDStr,
		Ticker:              ibReq.Ticker,
		Quantity:            ibReq.Quantity,
		PricePerUnit:        ibReq.PricePerUnit,
		Premium:             ibReq.Premium,
		SettlementAt:        settlementDate,
		ModifiedBy:          localUserIDStr,
		Status:              "pending",
	}
	if err := db.DB.Create(&trade).Error; err != nil {
		return ctx.Status(500).JSON(types.Response{false, "", "Greška pri čuvanju međubankarske ponude"})
	}

	return ctx.Status(201).JSON(types.Response{
		Success: true,
		Data:    fmt.Sprintf("Interbank ponuda kreirana, negoID=%s", fbid.ID),
	})
}

func (c *OTCTradeController) CounterOfferOTCTrade(ctx *fiber.Ctx) error {
	id := ctx.Params("id")
	var req UpdateOTCTradeRequest
	if err := ctx.BodyParser(&req); err != nil {
		return ctx.Status(fiber.StatusBadRequest).
			JSON(types.Response{Success: false, Data: "", Error: "Nevalidan JSON format"})
	}
	if err := c.validator.Struct(req); err != nil {
		return ctx.Status(fiber.StatusBadRequest).
			JSON(types.Response{Success: false, Data: "", Error: err.Error()})
	}

	settlementDate, err := time.Parse("2006-01-02", req.SettlementDate)
	if err != nil {
		return ctx.Status(fiber.StatusBadRequest).
			JSON(types.Response{Success: false, Data: "", Error: "Nevalidan format datuma. Očekivano YYYY-MM-DD"})
	}

	localUserID := uint(ctx.Locals("user_id").(float64))
	localUserIDStr := strconv.FormatUint(uint64(localUserID), 10)

	var trade types.OTCTrade
	if err := db.DB.Preload("Portfolio").First(&trade, id).Error; err != nil {
		return ctx.Status(fiber.StatusNotFound).
			JSON(types.Response{Success: false, Data: "", Error: "Ponuda nije pronađena"})
	}

	if trade.ModifiedBy == localUserIDStr {
		return ctx.Status(fiber.StatusForbidden).
			JSON(types.Response{Success: false, Data: "", Error: "Ne možete uzastopno menjati ponudu"})
	}

	if trade.RemoteNegotiationID == nil {
		if trade.PortfolioID == nil {
			return ctx.Status(fiber.StatusBadRequest).
				JSON(types.Response{Success: false, Data: "", Error: "Interna greška: nema PortfolioID"})
		}
		var portfolio types.Portfolio
		if err := db.DB.First(&portfolio, *trade.PortfolioID).Error; err != nil {
			return ctx.Status(fiber.StatusInternalServerError).
				JSON(types.Response{Success: false, Data: "", Error: "Greška pri proveri portfolija"})
		}
		if portfolio.PublicCount < req.Quantity {
			return ctx.Status(fiber.StatusBadRequest).
				JSON(types.Response{Success: false, Data: "", Error: "Nedovoljno javno dostupnih akcija"})
		}
		trade.Quantity = req.Quantity
		trade.PricePerUnit = req.PricePerUnit
		trade.Premium = req.Premium
		trade.SettlementAt = settlementDate
		trade.LastModified = time.Now().Unix()
		trade.ModifiedBy = localUserIDStr
		trade.Status = "pending"

		if err := db.DB.Save(&trade).Error; err != nil {
			return ctx.Status(fiber.StatusInternalServerError).
				JSON(types.Response{Success: false, Data: "", Error: "Greška prilikom čuvanja kontraponude"})
		}
		return ctx.Status(fiber.StatusOK).
			JSON(types.Response{Success: true, Data: fmt.Sprintf("Kontraponuda uspešno poslata (interna): %d", trade.ID), Error: ""})
	}

	const myRouting = 111

	var buyerFB, sellerFB dto.ForeignBankId
	if trade.RemoteBuyerID != nil && *trade.RemoteBuyerID == localUserIDStr {
		buyerFB = dto.ForeignBankId{RoutingNumber: myRouting, ID: localUserIDStr}
		sellerFB = dto.ForeignBankId{RoutingNumber: *trade.RemoteRoutingNumber, ID: *trade.RemoteSellerID}
	} else if trade.RemoteSellerID != nil && *trade.RemoteSellerID == localUserIDStr {
		buyerFB = dto.ForeignBankId{RoutingNumber: *trade.RemoteRoutingNumber, ID: *trade.RemoteBuyerID}
		sellerFB = dto.ForeignBankId{RoutingNumber: myRouting, ID: localUserIDStr}
	} else {
		return ctx.Status(fiber.StatusForbidden).
			JSON(types.Response{Success: false, Data: "", Error: "Niste učesnik ove međubankarske ponude"})
	}

	offer := dto.InterbankOtcOfferDTO{
		Stock:          dto.StockDescription{Ticker: trade.Ticker},
		SettlementDate: settlementDate.Format(time.RFC3339),
		PricePerUnit:   dto.MonetaryValue{Currency: "USD", Amount: req.PricePerUnit},
		Premium:        dto.MonetaryValue{Currency: "USD", Amount: req.Premium},
		Amount:         req.Quantity,
		BuyerID:        buyerFB,
		SellerID:       sellerFB,
		LastModifiedBy: dto.ForeignBankId{RoutingNumber: myRouting, ID: localUserIDStr},
	}

	body, err := json.Marshal(offer)
	if err != nil {
		return ctx.Status(500).
			JSON(types.Response{Success: false, Data: "", Error: "Greška pri serializaciji zahteva"})
	}

	url := fmt.Sprintf("%s/negotiations/%d/%s",
		os.Getenv("BANK4_BASE_URL"),
		*trade.RemoteRoutingNumber,
		*trade.RemoteNegotiationID,
	)
	httpReq, _ := http.NewRequest("PUT", url, bytes.NewReader(body))
	httpReq.Header.Set("Content-Type", "application/json")
	httpReq.Header.Set("X-Api-Key", os.Getenv("BANK4_API_KEY"))

	resp, err := http.DefaultClient.Do(httpReq)
	if err != nil {
		return ctx.Status(502).
			JSON(types.Response{Success: false, Data: "", Error: "Greška pri komunikaciji sa Bankom 4"})
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusConflict {
		return ctx.Status(409).
			JSON(types.Response{Success: false, Data: "", Error: "Nije vaš red za kontra‑ponudu."})
	}
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		respBody, _ := io.ReadAll(resp.Body)
		return ctx.Status(resp.StatusCode).
			JSON(types.Response{Success: false, Data: "", Error: fmt.Sprintf("Bank4: %s", string(respBody))})
	}

	trade.Quantity = req.Quantity
	trade.PricePerUnit = req.PricePerUnit
	trade.Premium = req.Premium
	trade.SettlementAt = settlementDate
	trade.LastModified = time.Now().Unix()
	trade.ModifiedBy = localUserIDStr
	trade.Status = "pending"

	if err := db.DB.Save(&trade).Error; err != nil {
		return ctx.Status(500).
			JSON(types.Response{Success: false, Data: "", Error: "Greška pri čuvanju međubankarske kontraponude"})
	}

	return ctx.Status(fiber.StatusOK).
		JSON(types.Response{Success: true, Data: fmt.Sprintf("Interbank kontraponuda poslata za međubankarski negotioationID: %d", trade.RemoteNegotiationID), Error: ""})
}

//	func (c *OTCTradeController) AcceptOTCTrade(ctx *fiber.Ctx) error {
//		id := ctx.Params("id")
//		userID := uint(ctx.Locals("user_id").(float64))
//
//		var trade types.OTCTrade
//		if err := db.DB.Preload("Portfolio").First(&trade, id).Error; err != nil {
//			return ctx.Status(fiber.StatusNotFound).JSON(types.Response{
//				Success: false,
//				Error:   "Ponuda nije pronađena",
//			})
//		}
//
//		if trade.ModifiedBy != nil && *trade.ModifiedBy == userID {
//			return ctx.Status(fiber.StatusForbidden).JSON(types.Response{
//				Success: false,
//				Error:   "Nemate pravo da prihvatite ovu ponudu jer ste je vi poslednji menjali",
//			})
//		}
//
//		if trade.Status == "accepted" || trade.Status == "executed" {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Ova ponuda je već prihvaćena ili realizovana",
//			})
//		}
//
//		var portfolio types.Portfolio
//		if err := db.DB.First(&portfolio, trade.PortfolioID).Error; err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška pri dohvatanju portfolija",
//			})
//		}
//
//		var existingContracts []types.OptionContract
//		if err := db.DB.
//			Where("seller_id = ? AND portfolio_id = ? AND is_exercised = false AND status = ?", trade.SellerID, portfolio.ID, "active").
//			Find(&existingContracts).Error; err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška pri proveri postojećih ugovora",
//			})
//		}
//
//		usedQuantity := 0
//		for _, contract := range existingContracts {
//			usedQuantity += contract.Quantity
//		}
//
//		if usedQuantity+trade.Quantity > portfolio.PublicCount {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Nemate dovoljno raspoloživih akcija za prihvatanje ove ponude",
//			})
//		}
//
//		trade.Status = "accepted"
//		trade.LastModified = time.Now().Unix()
//		trade.ModifiedBy = &userID
//		if err := db.DB.Save(&trade).Error; err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška pri ažuriranju ponude",
//			})
//		}
//
//		contract := types.OptionContract{
//			OTCTradeID:   trade.ID,
//			BuyerID:      *trade.BuyerID,
//			SellerID:     trade.SellerID,
//			PortfolioID:  trade.PortfolioID,
//			Quantity:     trade.Quantity,
//			StrikePrice:  trade.PricePerUnit,
//			SecurityID:   trade.SecurityId,
//			Premium:      trade.Premium,
//			Status:       "active",
//			SettlementAt: trade.SettlementAt,
//			IsExercised:  false,
//			CreatedAt:    time.Now().Unix(),
//		}
//
//		buyerAccounts, err := broker.GetAccountsForUser(int64(contract.BuyerID))
//		if err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Neuspešno dohvatanje računa kupca",
//			})
//		}
//
//		sellerAccounts, err := broker.GetAccountsForUser(int64(contract.SellerID))
//		if err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Neuspešno dohvatanje računa prodavca",
//			})
//		}
//
//		var buyerAccountID, sellerAccountID int64 = -1, -1
//
//		for _, acc := range buyerAccounts {
//			if acc.CurrencyType == "USD" {
//				buyerAccountID = acc.ID
//				break
//			}
//		}
//
//		// Ako nema USD racun,propbati RSD
//		// if buyerAccountID == -1 {
//		// 	for _, acc := range buyerAccounts {
//		// 		if acc.CurrencyType == "RSD" {
//		// 			buyerAccountID = acc.ID
//		// 			break
//		// 		}
//		// 	}
//		// }
//
//		for _, acc := range sellerAccounts {
//			if acc.CurrencyType == "USD" {
//				sellerAccountID = acc.ID
//				break
//			}
//		}
//		// Ako nema USD racun,propbati RSD
//		// if sellerAccountID == -1 {
//		// 	for _, acc := range sellerAccounts {
//		// 		if acc.CurrencyType == "RSD" {
//		// 			sellerAccountID = acc.ID
//		// 			break
//		// 		}
//		// 	}
//		// }
//		// DODATI OBRADU VALUTI RACUNA NA BANKING STRANI
//		if buyerAccountID == -1 || sellerAccountID == -1 {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Kupac ili prodavac nema USD račun",
//			})
//		}
//
//		var buyerAccount *dto.Account
//		for _, acc := range buyerAccounts {
//			if acc.ID == buyerAccountID {
//				buyerAccount = &acc
//				break
//			}
//		}
//		if buyerAccount == nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška pri pronalaženju kupčevog računa",
//			})
//		}
//
//		if buyerAccount.Balance < contract.Premium {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Kupčev račun nema dovoljno sredstava za plaćanje premije",
//			})
//		}
//
//		if err := db.DB.Create(&contract).Error; err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška pri kreiranju ugovora " + err.Error(),
//			})
//		}
//
//		premiumDTO := &dto.OTCPremiumFeeDTO{
//			BuyerAccountId:  uint(buyerAccountID),
//			SellerAccountId: uint(sellerAccountID),
//			Amount:          contract.Premium,
//		}
//
//		if err := broker.SendOTCPremium(premiumDTO); err != nil {
//			_ = db.DB.Delete(&contract)
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška pri plaćanju premije",
//			})
//		}
//
//		return ctx.Status(fiber.StatusOK).JSON(types.Response{
//			Success: true,
//			Data:    fmt.Sprintf("Ponuda uspešno prihvaćena.Premija uspešno isplaćena.Kreiran ugovor: %d", contract.ID),
//		})
//	}

//	func (c *OTCTradeController) ExecuteOptionContract(ctx *fiber.Ctx) error {
//		id := ctx.Params("id")
//		userID := uint(ctx.Locals("user_id").(float64))
//
//		var contract types.OptionContract
//		if err := db.DB.First(&contract, id).Error; err != nil {
//			return ctx.Status(fiber.StatusNotFound).JSON(types.Response{
//				Success: false,
//				Error:   "Ugovor nije pronađen",
//			})
//		}
//
//		if contract.BuyerID != userID {
//			return ctx.Status(fiber.StatusForbidden).JSON(types.Response{
//				Success: false,
//				Error:   "Nemate pravo da izvršite ovaj ugovor",
//			})
//		}
//
//		if contract.IsExercised {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Ovaj ugovor je već iskorišćen",
//			})
//		}
//
//		if contract.SettlementAt.Before(time.Now()) {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Ugovor je istekao",
//			})
//		}
//
//		buyerAccounts, err := broker.GetAccountsForUser(int64(contract.BuyerID))
//		if err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Neuspešno dohvatanje računa kupca",
//			})
//		}
//
//		sellerAccounts, err := broker.GetAccountsForUser(int64(contract.SellerID))
//		if err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Neuspešno dohvatanje računa prodavca",
//			})
//		}
//
//		var buyerAccountID, sellerAccountID int64 = -1, -1
//
//		var buyerAccount *dto.Account
//		for _, acc := range buyerAccounts {
//			if acc.CurrencyType == "USD" {
//				buyerAccountID = acc.ID
//				buyerAccount = &acc
//				break
//			}
//		}
//
//		// if buyerAccountID == -1 {
//		// 	for _, acc := range buyerAccounts {
//		// 		if acc.CurrencyType == "RSD" {
//		// 			buyerAccountID = acc.ID
//		// 			break
//		// 		}
//		// 	}
//		// }
//
//		for _, acc := range sellerAccounts {
//			if acc.CurrencyType == "USD" {
//				sellerAccountID = acc.ID
//				break
//			}
//		}
//
//		// if sellerAccountID == -1 {
//		// 	for _, acc := range sellerAccounts {
//		// 		if acc.CurrencyType == "RSD" {
//		// 			sellerAccountID = acc.ID
//		// 			break
//		// 		}
//		// 	}
//		// }
//		if buyerAccountID == -1 || sellerAccountID == -1 {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Kupac ili prodavac nema USD račun",
//			})
//		}
//
//		if buyerAccount.Balance < (contract.StrikePrice * float64(contract.Quantity)) {
//			return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//				Success: false,
//				Error:   "Kupčev račun nema dovoljno sredstava za izvršavanje ugovora",
//			})
//		}
//
//		uid := fmt.Sprintf("OTC-%d-%d", contract.ID, time.Now().Unix())
//
//		dto := &types.OTCTransactionInitiationDTO{
//			Uid:             uid,
//			SellerAccountId: uint(sellerAccountID),
//			BuyerAccountId:  uint(buyerAccountID),
//			Amount:          contract.StrikePrice * float64(contract.Quantity),
//		}
//
//		contract.UID = uid
//
//		if err := db.DB.Save(&contract).Error; err != nil {
//			go broker.FailOTC(uid, "Greška prilikom čuvanja statusa ugovora")
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška prilikom čuvanja statusa ugovora",
//			})
//		}
//
//		if err := db.DB.Model(&types.OTCTrade{}).Where("id = ?", contract.OTCTradeID).Update("status", "completed").Error; err != nil {
//			go broker.FailOTC(uid, "Greška pri ažuriranju OTC ponude")
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška pri ažuriranju OTC ponude",
//			})
//		}
//
//		if err := saga.StateManager.UpdatePhase(db.DB, uid, types.PhaseInit); err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška prilikom kreiranja OTC transakcije",
//			})
//		}
//
//		if err := broker.SendOTCTransactionInit(dto); err != nil {
//			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//				Success: false,
//				Error:   "Greška prilikom slanja OTC transakcije",
//			})
//		}
//
//		return ctx.Status(fiber.StatusOK).JSON(types.Response{
//			Success: true,
//			Data:    "Ugovor uspešno realizovan",
//		})
//	}
func (c *OTCTradeController) GetActiveOffers(ctx *fiber.Ctx) error {
	userID := uint(ctx.Locals("user_id").(float64))
	userIDStr := strconv.FormatUint(uint64(userID), 10)

	var trades []types.OTCTrade
	if err := db.DB.
		Preload("Portfolio.Security").
		Where("status = ?", "pending").
		Where("(local_buyer_id = ? OR local_seller_id = ? OR remote_buyer_id = ? OR remote_seller_id = ?)",
			userID, userID, userIDStr, userIDStr).
		Find(&trades).Error; err != nil {
		return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
			Success: false,
			Error:   "Greška prilikom dohvatanja aktivnih ponuda",
		})
	}

	return ctx.JSON(types.Response{
		Success: true,
		Data:    trades,
	})
}

//
//func (c *OTCTradeController) GetUserOptionContracts(ctx *fiber.Ctx) error {
//	userID := uint(ctx.Locals("user_id").(float64))
//	var contracts []types.OptionContract
//
//	if err := db.DB.
//		Preload("Portfolio.Security").
//		Preload("OTCTrade.Portfolio.Security").
//		Where("buyer_id = ? OR seller_id = ?", userID, userID).
//		Find(&contracts).Error; err != nil {
//		return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//			Success: false,
//			Error:   "Greška prilikom dohvatanja ugovora",
//		})
//	}
//
//	return ctx.JSON(types.Response{
//		Success: true,
//		Data:    contracts,
//	})
//}

//func (c *OTCTradeController) RejectOTCTrade(ctx *fiber.Ctx) error {
//	id := ctx.Params("id")
//	userID := uint(ctx.Locals("user_id").(float64))
//
//	var trade types.OTCTrade
//	if err := db.DB.First(&trade, id).Error; err != nil {
//		return ctx.Status(fiber.StatusNotFound).JSON(types.Response{
//			Success: false,
//			Error:   "Ponuda nije pronađena",
//		})
//	}
//
//	if trade.ModifiedBy != nil && *trade.ModifiedBy == userID {
//		return ctx.Status(fiber.StatusForbidden).JSON(types.Response{
//			Success: false,
//			Error:   "Ne možete odbiti sopstvenu ponudu",
//		})
//	}
//
//	if trade.Status != "pending" {
//		return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
//			Success: false,
//			Error:   "Ponuda više nije aktivna",
//		})
//	}
//
//	trade.Status = "rejected"
//	trade.LastModified = time.Now().Unix()
//	trade.ModifiedBy = &userID
//
//	if err := db.DB.Save(&trade).Error; err != nil {
//		return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
//			Success: false,
//			Error:   "Greška pri odbijanju ponude",
//		})
//	}
//
//	return ctx.JSON(types.Response{
//		Success: true,
//		Data:    "Ponuda je uspešno odbijena",
//	})
//}

type PortfolioControllerr struct{}

func NewPortfolioControllerr() *PortfolioControllerr {
	return &PortfolioControllerr{}
}

func InitPortfolioRoutess(app *fiber.App) {
	portfolioController := NewPortfolioControllerr()

	app.Get("/portfolio/public", middlewares.Auth, portfolioController.GetOurAndInterPublicPortfolios)
}

func GetPublicStocks(ctx *fiber.Ctx) error {
	const myRoutingNumber = 111

	var portfolios []types.Portfolio
	if err := db.DB.Preload("Security").Where("public_count > 0").Find(&portfolios).Error; err != nil {
		return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
			Success: false,
			Error:   "Greška prilikom dohvatanja portfolija",
		})
	}

	grouped := map[string][]dto.SellerStockEntry{}

	for _, p := range portfolios {
		if !strings.EqualFold(p.Security.Type, "Stock") {
			continue
		}

		ticker := p.Security.Ticker
		entry := dto.SellerStockEntry{
			Seller: dto.ForeignBankId{
				RoutingNumber: myRoutingNumber,
				ID:            fmt.Sprintf("%d", p.UserID),
			},
			Amount: p.PublicCount,
		}

		grouped[ticker] = append(grouped[ticker], entry)
	}

	var result dto.PublicStocksResponse
	for ticker, sellers := range grouped {
		result = append(result, dto.PublicStock{
			Stock:   dto.StockDescription{Ticker: ticker},
			Sellers: sellers,
		})
	}

	return ctx.JSON(result)
}

type UnifiedPublicPortfolio struct {
	Ticker       string   `json:"ticker"`
	Quantity     int      `json:"quantity"`
	Price        *float64 `json:"price,omitempty"`
	SecurityName *string  `json:"name,omitempty"`
	PortfolioID  *uint    `json:"portfolioId,omitempty"`
	OwnerID      string   `json:"ownerId"`
}

func fetchForeignPublicStocks() ([]UnifiedPublicPortfolio, error) {
	foreignURL := os.Getenv("BANK4_PUBLIC_STOCK_URL")
	resp, err := http.Get(foreignURL)
	if err != nil {
		return nil, fmt.Errorf("Greška prilikom slanja zahteva ka banci 4: %w", err)
	}
	defer resp.Body.Close()

	var foreignStocks dto.PublicStocksResponse
	if err := json.NewDecoder(resp.Body).Decode(&foreignStocks); err != nil {
		return nil, fmt.Errorf("Neuspešno parsiranje podataka iz banke 4: %w", err)
	}

	var result []UnifiedPublicPortfolio
	for _, ps := range foreignStocks {
		var sec types.Security
		var namePtr *string
		if err := db.DB.Where("ticker = ?", ps.Stock.Ticker).First(&sec).Error; err == nil {
			namePtr = &sec.Name
		}

		for _, seller := range ps.Sellers {
			ownerID := fmt.Sprintf("%d%s", seller.Seller.RoutingNumber, seller.Seller.ID)
			result = append(result, UnifiedPublicPortfolio{
				Ticker:       ps.Stock.Ticker,
				Quantity:     seller.Amount,
				Price:        nil,
				SecurityName: namePtr,
				PortfolioID:  nil,
				OwnerID:      ownerID,
			})
		}
	}

	return result, nil
}

func (c *PortfolioControllerr) GetOurAndInterPublicPortfolios(ctx *fiber.Ctx) error {
	userID := uint(ctx.Locals("user_id").(float64))

	// 1. domaći portfoliji
	var localPortfolios []types.Portfolio
	if err := db.DB.
		Where("public_count > 0 AND user_id != ?", userID).
		Preload("Security").
		Find(&localPortfolios).Error; err != nil {
		return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
			Success: false,
			Error:   "Greška prilikom dohvatanja domaćih portfolija",
		})
	}

	var result []UnifiedPublicPortfolio
	for _, p := range localPortfolios {
		if !strings.EqualFold(p.Security.Type, "Stock") {
			continue
		}
		price := p.PurchasePrice
		name := p.Security.Name
		ownerID := strconv.FormatUint(uint64(p.UserID), 10)

		result = append(result, UnifiedPublicPortfolio{
			Ticker:       p.Security.Ticker,
			Quantity:     p.PublicCount,
			Price:        &price,
			SecurityName: &name,
			PortfolioID:  &p.ID,
			OwnerID:      ownerID,
		})
	}

	foreign, err := fetchForeignPublicStocks()
	if err != nil {
		fmt.Println("[UPOZORENJE] Greška u dohvatanju stranih portfolija:", err)
	} else {
		result = append(result, foreign...)
	}

	return ctx.JSON(types.Response{
		Success: true,
		Data:    result,
	})
}

type CreateInterbankOTCOfferRequest struct {
	Ticker         string  `json:"ticker" validate:"required"`
	Quantity       int     `json:"quantity" validate:"required,gt=0"`
	PricePerUnit   float64 `json:"pricePerUnit" validate:"required,gt=0"`
	Premium        float64 `json:"premium" validate:"required,gte=0"`
	SettlementDate string  `json:"settlementDate" validate:"required"`
	SellerRouting  int     `json:"sellerRouting" validate:"required"`
	SellerID       string  `json:"sellerId" validate:"required"`
}

func (c *OTCTradeController) GetInterbankNegotiation(ctx *fiber.Ctx) error {
	routingStr := ctx.Params("routingNumber")
	negID := ctx.Params("id")

	routingNum, err := strconv.Atoi(routingStr)
	if err != nil {
		return ctx.Status(fiber.StatusBadRequest).JSON(types.Response{
			Success: false,
			Error:   "Neispravan routingNumber",
		})
	}

	var t types.OTCTrade
	if err := db.DB.
		Where("remote_routing_number = ? AND remote_negotiation_id = ?", routingNum, negID).
		First(&t).Error; err != nil {
		return ctx.Status(fiber.StatusNotFound).JSON(types.Response{
			Success: false,
			Error:   "Negotiation not found",
		})
	}

	var buyerFB, sellerFB dto.ForeignBankId
	const myRouting = 111

	if t.LocalBuyerID != nil && t.LocalSellerID != nil {
		buyerFB = dto.ForeignBankId{RoutingNumber: myRouting, ID: fmt.Sprint(*t.LocalBuyerID)}
		sellerFB = dto.ForeignBankId{RoutingNumber: myRouting, ID: fmt.Sprint(*t.LocalSellerID)}
	} else {
		if t.RemoteRoutingNumber == nil || t.RemoteBuyerID == nil || t.RemoteSellerID == nil {
			return ctx.Status(fiber.StatusInternalServerError).JSON(types.Response{
				Success: false,
				Error:   "Nepotpuni podaci za međubankarsku ponudu",
			})
		}
		buyerFB = dto.ForeignBankId{RoutingNumber: *t.RemoteRoutingNumber, ID: *t.RemoteBuyerID}
		sellerFB = dto.ForeignBankId{RoutingNumber: *t.RemoteRoutingNumber, ID: *t.RemoteSellerID}
	}

	lmb := t.ModifiedBy
	lmbRouting, _ := strconv.Atoi(lmb[:3])
	lastMod := dto.ForeignBankId{
		RoutingNumber: lmbRouting,
		ID:            lmb[3:],
	}

	nt := dto.OtcNegotiation{
		Stock:          dto.StockDescription{Ticker: t.Ticker},
		SettlementDate: t.SettlementAt.Format(time.RFC3339),
		PricePerUnit:   dto.MonetaryValue{Currency: "USD", Amount: t.PricePerUnit},
		Premium:        dto.MonetaryValue{Currency: "USD", Amount: t.Premium},
		BuyerID:        buyerFB,
		SellerID:       sellerFB,
		Amount:         t.Quantity,
		LastModifiedBy: lastMod,
		IsOngoing:      t.Status == "pending",
	}

	return ctx.JSON(types.Response{
		Success: true,
		Data:    nt,
	})
}

func InitOTCTradeRoutes(app *fiber.App) {
	app.Get("/public-stock", GetPublicStocks)
	otcController := NewOTCTradeController()
	otc := app.Group("/otctrade", middlewares.Auth)
	// dostupno svima (bankama) bez user‑auth
	app.Get("/negotiations/:routingNumber/:id", otcController.GetInterbankNegotiation)
	otc.Post("/offer", middlewares.RequirePermission("user.customer.otc_trade"), otcController.CreateOTCTrade)
	otc.Put("/offer/:id/counter", middlewares.RequirePermission("user.customer.otc_trade"), otcController.CounterOfferOTCTrade)
	//otc.Put("/offer/:id/accept", middlewares.RequirePermission("user.customer.otc_trade"), otcController.AcceptOTCTrade)
	//otc.Put("/offer/:id/reject", middlewares.RequirePermission("user.customer.otc_trade"), otcController.RejectOTCTrade)
	//otc.Put("/option/:id/execute", middlewares.RequirePermission("user.customer.otc_trade"), otcController.ExecuteOptionContract)
	otc.Get("/offer/active", otcController.GetActiveOffers)
	//otc.Get("/option/contracts", otcController.GetUserOptionContracts)
}
