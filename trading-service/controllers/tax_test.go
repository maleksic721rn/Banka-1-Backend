package controllers

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"banka1.com/types"
	"github.com/gofiber/fiber/v2"
	"github.com/stretchr/testify/assert"
)

// func TestRunTax_Success_Database_Assertions(t *testing.T) {
// 	app := fiber.New()
// 	taxController := NewTaxController()
// 	app.Post("/tax/run", taxController.RunTax)

// 	db.DB.Exec("DELETE FROM transactions")

// 	db.DB.Exec(`
// 		INSERT INTO transactions (id, order_id, buyer_id, seller_id, security_id, quantity, price_per_unit, total_price, tax_paid, created_at) VALUES
// 		(1, 1, 1, 2, 101, 10, 25.0, 250.0, FALSE, strftime('%Y-%m-%d', 'now')),
// 		(2, 2, 2, 1, 102, 5, 20.0, 100.0, FALSE, strftime('%Y-%m-%d', 'now'))
// 	`)

// 	req := httptest.NewRequest(http.MethodPost, "/tax/run", nil)
// 	resp, _ := app.Test(req)
// 	defer resp.Body.Close()

// 	assert.Equal(t, 202, resp.StatusCode)

// 	body, _ := io.ReadAll(resp.Body)
// 	var response types.Response
// 	json.Unmarshal(body, &response)

// 	assert.True(t, response.Success)
// 	assert.Equal(t, "Tax calculation and deduction completed successfully.", response.Data)

// 	var taxPaidCount int
// 	db.DB.Raw("SELECT COUNT(*) FROM transactions WHERE tax_paid = TRUE").Scan(&taxPaidCount)
// 	assert.Equal(t, 2, taxPaidCount)

// }

// func TestRunTax_Success(t *testing.T) {
// 	app := fiber.New()
// 	taxController := NewTaxController()
// 	app.Post("/tax/run", taxController.RunTax)

// 	req := httptest.NewRequest(http.MethodPost, "/tax/run", nil)
// 	resp, _ := app.Test(req)
// 	defer resp.Body.Close()

// 	assert.Equal(t, 202, resp.StatusCode)

// 	body, _ := io.ReadAll(resp.Body)
// 	var response types.Response
// 	json.Unmarshal(body, &response)

// 	assert.True(t, response.Success)
// 	assert.Contains(t, response.Data, "Tax calculation and deduction completed successfully.")
// }

func TestGetAggregatedTaxForUser_InvalidUserID(t *testing.T) {
	// Setup
	app := fiber.New()
	taxController := NewTaxController()
	app.Get("/tax/dashboard/:userID", taxController.GetAggregatedTaxForUser)

	// Test case: Invalid user ID (not a number)
	req := httptest.NewRequest(http.MethodGet, "/tax/dashboard/invalid", nil)
	resp, _ := app.Test(req)
	defer resp.Body.Close()

	// Assertions
	assert.Equal(t, 400, resp.StatusCode)

	body, _ := io.ReadAll(resp.Body)
	var response types.Response
	json.Unmarshal(body, &response)

	assert.False(t, response.Success)
	assert.Contains(t, response.Error, "Neispravan userID parametar")

	// Test case: User ID <= 0
	req = httptest.NewRequest(http.MethodGet, "/tax/dashboard/0", nil)
	resp, _ = app.Test(req)
	defer resp.Body.Close()

	// Assertions
	assert.Equal(t, 400, resp.StatusCode)

	body, _ = io.ReadAll(resp.Body)
	json.Unmarshal(body, &response)

	assert.False(t, response.Success)
	assert.Contains(t, response.Error, "Neispravan userID parametar")
}

func TestInitTaxRoutes(t *testing.T) {
	// Setup
	app := fiber.New()

	// Execute InitTaxRoutes
	InitTaxRoutes(app)

	// This is primarily a smoke test to ensure no panics
	// We can also verify each route is registered by examining app.Stack()

	// Helper function to find routes by method and path
	findRoute := func(method, path string) bool {
		for _, routes := range app.Stack() {
			for _, route := range routes {
				if route.Method == method && strings.HasSuffix(route.Path, path) {
					return true
				}
			}
		}
		return false
	}

	// Verify routes are registered
	assert.True(t, findRoute("GET", "/tax"))
	assert.True(t, findRoute("POST", "/tax/run"))
	assert.True(t, findRoute("GET", "/tax/dashboard/:userID"))
}
