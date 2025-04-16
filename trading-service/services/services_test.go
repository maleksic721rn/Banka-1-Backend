package services

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"testing"
	"time"

	"banka1.com/db"
	"banka1.com/dto"
	"github.com/DATA-DOG/go-sqlmock"
	"github.com/gofiber/fiber/v2"
	"github.com/stretchr/testify/assert"
	"github.com/valyala/fasthttp"
	"gorm.io/driver/postgres"
	"gorm.io/gorm"
)

// MockRoundTripper implements http.RoundTripper for testing
type MockRoundTripper struct {
    Response *http.Response
    Error    error
}

func (m *MockRoundTripper) RoundTrip(_ *http.Request) (*http.Response, error) {
    return m.Response, m.Error
}



// createFiberCtxWithToken creates a Fiber context with a token for testing
func createFiberCtxWithToken(token string) *fiber.Ctx {
    app := fiber.New()
    ctx := app.AcquireCtx(&fasthttp.RequestCtx{})
    if token != "" {
        ctx.Locals("token", token)
    }
    return ctx
}

func TestGetEmployeesFiltered_Success(t *testing.T) {
    // Save original http.Client and restore it after test
    originalClient := http.DefaultClient
    defer func() { http.DefaultClient = originalClient }()

    // Set environment variable
    os.Setenv("USER_SERVICE", "http://mock-service")
    defer os.Unsetenv("USER_SERVICE")

    // Prepare mock response
    expectedResponse := dto.FilteredActuaryResponse{
        Data: []dto.FilteredActuaryDTO{
            {
                ID:             1,
                FirstName:      "John",
                LastName:       "Doe",
                Email:          "john.doe@example.com",
                Department:     "Finance",
                Position:       "Actuary",
                LimitAmount:    10000,
                UsedLimit:      5000,
            },
        },
    }

    // Setup test server
    server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        // Verify request headers and query parameters
        assert.Equal(t, "Bearer test-token", r.Header.Get("Authorization"))
        assert.Equal(t, "John", r.URL.Query().Get("firstName"))
        assert.Equal(t, "Doe", r.URL.Query().Get("lastName"))
        assert.Equal(t, "john@example.com", r.URL.Query().Get("email"))
        assert.Equal(t, "Actuary", r.URL.Query().Get("position"))

        // Return successful response
        w.WriteHeader(http.StatusOK)
        json.NewEncoder(w).Encode(expectedResponse)
    }))
    defer server.Close()

    os.Setenv("USER_SERVICE", server.URL)

    // Create test fiber context with token
    ctx := createFiberCtxWithToken("test-token")

    // Call the function we're testing
    employees, err := GetEmployeesFiltered(ctx, "John", "Doe", "john@example.com", "Actuary")

    // Assertions
    assert.NoError(t, err)
    assert.NotNil(t, employees)
    assert.Equal(t, 1, len(employees))
    assert.Equal(t, uint(1), employees[0].ID)
    assert.Equal(t, "John", employees[0].FirstName)
    assert.Equal(t, "Doe", employees[0].LastName)
    assert.Equal(t, "john.doe@example.com", employees[0].Email)
}

func TestGetEmployeesFiltered_MissingToken(t *testing.T) {
    // Create context without token
    ctx := createFiberCtxWithToken("")

    // Call function
    employees, err := GetEmployeesFiltered(ctx, "", "", "", "")

    // Assertions
    assert.Error(t, err)
    assert.Nil(t, employees)
    assert.Contains(t, err.Error(), "token nije pronađen")
}

func TestGetEmployeesFiltered_MissingEnvironmentVariable(t *testing.T) {
    // Ensure environment variable is unset
    os.Unsetenv("USER_SERVICE")

    // Create context with token
    ctx := createFiberCtxWithToken("test-token")

    // Call function
    employees, err := GetEmployeesFiltered(ctx, "", "", "", "")

    // Assertions
    assert.Error(t, err)
    assert.Nil(t, employees)
    assert.Contains(t, err.Error(), "USER_SERVICE environment variable is not set")
}

func TestGetEmployeesFiltered_HttpError(t *testing.T) {
    // Set environment variable
    os.Setenv("USER_SERVICE", "http://invalid-service")
    defer os.Unsetenv("USER_SERVICE")

    // Create context with token
    ctx := createFiberCtxWithToken("test-token")

    // Call function
    employees, err := GetEmployeesFiltered(ctx, "", "", "", "")

    // Assertions
    assert.Error(t, err)
    assert.Nil(t, employees)
}

func TestGetEmployeesFiltered_NonOkStatus(t *testing.T) {
    // Set environment variable
    os.Setenv("USER_SERVICE", "http://mock-service")
    defer os.Unsetenv("USER_SERVICE")

    // Setup test server returning non-OK status
    server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        w.WriteHeader(http.StatusUnauthorized)
        w.Write([]byte("Unauthorized"))
    }))
    defer server.Close()

    os.Setenv("USER_SERVICE", server.URL)

    // Create context with token
    ctx := createFiberCtxWithToken("test-token")

    // Call function
    employees, err := GetEmployeesFiltered(ctx, "", "", "", "")

    // Assertions
    assert.Error(t, err)
    assert.Nil(t, employees)
    assert.Contains(t, err.Error(), "status: 401")
}

func TestGetEmployeesFiltered_InvalidJson(t *testing.T) {
    // Set environment variable
    os.Setenv("USER_SERVICE", "http://mock-service")
    defer os.Unsetenv("USER_SERVICE")

    // Setup test server returning invalid JSON
    server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
        w.WriteHeader(http.StatusOK)
        w.Write([]byte("{invalid json}"))
    }))
    defer server.Close()

    os.Setenv("USER_SERVICE", server.URL)

    // Create context with token
    ctx := createFiberCtxWithToken("test-token")

    // Call function
    employees, err := GetEmployeesFiltered(ctx, "", "", "", "")

    // Assertions
    assert.Error(t, err)
    assert.Nil(t, employees)
}

func TestCalcylateRealizedProfit(t *testing.T) {
        // Setup test DB
        oldDB := db.DB
        mockDB, mock, err := sqlmock.New()
        if err != nil {
            t.Fatalf("Failed to create mock DB: %v", err)
        }
        defer mockDB.Close()
        
        gormDB, err := gorm.Open(postgres.New(postgres.Config{
            Conn: mockDB,
        }), &gorm.Config{})
        if err != nil {
            t.Fatalf("Failed to open GORM DB: %v", err)
        }
        db.DB = gormDB
        defer func() { db.DB = oldDB }()
        
        userID := uint(1)
        
        // Mock query for transactions
        rows := sqlmock.NewRows([]string{"id", "security_id", "buyer_id", "seller_id", "quantity", "price_per_unit", "created_at"}).
            AddRow(1, 100, userID, 2, 10, 100.0, time.Now().Add(-2*time.Hour)).  // Buy 10 shares at $100
            AddRow(2, 100, 3, userID, 5, 120.0, time.Now().Add(-1*time.Hour))     // Sell 5 shares at $120
        
        mock.ExpectQuery("SELECT .+ FROM \"transactions\"").
            WithArgs(userID, userID).
            WillReturnRows(rows)
        
        // Mock query for security ticker
        securityRows := sqlmock.NewRows([]string{"ticker"}).AddRow("AAPL")
        mock.ExpectQuery("SELECT \"ticker\" FROM \"securities\"").
            WithArgs(100).
            WillReturnRows(securityRows)
        
        // Call the function
        result, err := CalculateRealizedProfit(userID)
        
        // Verify results
        assert.NoError(t, err)
        assert.NotNil(t, result)
        assert.Equal(t, userID, result.UserID)
        assert.Equal(t, 100.0, result.TotalProfit) // (120-100)*5 = 100
        assert.Equal(t, 1, len(result.PerSecurity))
        assert.Equal(t, "UNKNOWN", result.PerSecurity[0].Ticker)
    }

    func TestCalculateRealizedProfit_NoTransactions(t *testing.T) {
        // Setup test DB
        oldDB := db.DB
        mockDB, mock, err := sqlmock.New()
        if err != nil {
            t.Fatalf("Failed to create mock DB: %v", err)
        }
        defer mockDB.Close()
        
        gormDB, err := gorm.Open(postgres.New(postgres.Config{
            Conn: mockDB,
        }), &gorm.Config{})
        if err != nil {
            t.Fatalf("Failed to open GORM DB: %v", err)
        }
        db.DB = gormDB
        defer func() { db.DB = oldDB }()
        
        userID := uint(1)
        
        // Mock empty query result
        rows := sqlmock.NewRows([]string{"id", "security_id", "buyer_id", "seller_id", "quantity", "price_per_unit", "created_at"})
        
        mock.ExpectQuery("SELECT .+ FROM \"transactions\"").
            WithArgs(userID, userID).
            WillReturnRows(rows)
        
        // Call the function
        result, err := CalculateRealizedProfit(userID)
        
        // Verify error is returned
        assert.Error(t, err)
        assert.Nil(t, result)
        assert.Contains(t, err.Error(), "nema transakcija")
    }

    func TestCalculateRealizedProfit_ComplexScenario(t *testing.T) {
        // Setup test DB
        oldDB := db.DB
        mockDB, mock, err := sqlmock.New()
        if err != nil {
            t.Fatalf("Failed to create mock DB: %v", err)
        }
        defer mockDB.Close()
        
        gormDB, err := gorm.Open(postgres.New(postgres.Config{
            Conn: mockDB,
        }), &gorm.Config{})
        if err != nil {
            t.Fatalf("Failed to open GORM DB: %v", err)
        }
        db.DB = gormDB
        defer func() { db.DB = oldDB }()
        
        userID := uint(1)
        
        // Mock query for transactions with multiple securities
        rows := sqlmock.NewRows([]string{"id", "security_id", "buyer_id", "seller_id", "quantity", "price_per_unit", "created_at"}).
            // AAPL transactions
            AddRow(1, 100, userID, 2, 10, 100.0, time.Now().Add(-4*time.Hour)).  // Buy 10 AAPL at $100
            AddRow(2, 100, userID, 3, 5, 110.0, time.Now().Add(-3*time.Hour)).   // Buy 5 AAPL at $110
            AddRow(3, 100, 4, userID, 12, 130.0, time.Now().Add(-2*time.Hour)).  // Sell 12 AAPL at $130
            // MSFT transactions  
            AddRow(4, 200, userID, 5, 20, 50.0, time.Now().Add(-4*time.Hour)).   // Buy 20 MSFT at $50
            AddRow(5, 200, 6, userID, 20, 45.0, time.Now().Add(-1*time.Hour))    // Sell 20 MSFT at $45 (loss)
        
        mock.ExpectQuery("SELECT .+ FROM \"transactions\"").
            WithArgs(userID, userID).
            WillReturnRows(rows)
        
        // Mock security ticker queries
        mock.ExpectQuery("SELECT \"ticker\" FROM \"securities\"").
            WithArgs(100).
            WillReturnRows(sqlmock.NewRows([]string{"ticker"}).AddRow("AAPL"))
            
        mock.ExpectQuery("SELECT \"ticker\" FROM \"securities\"").
            WithArgs(200).
            WillReturnRows(sqlmock.NewRows([]string{"ticker"}).AddRow("MSFT"))
        
        // Call the function
        result, err := CalculateRealizedProfit(userID)
        
        // Verify results
        assert.NoError(t, err)
        assert.NotNil(t, result)
        assert.Equal(t, userID, result.UserID)
        
        // Expected calculations:
        // AAPL: (130-100)*10 + (130-110)*2 = 300 + 40 = 340
        // MSFT: (45-50)*20 = -100
        // Total: 340 - 100 = 240
        assert.Equal(t, 240.0, result.TotalProfit)
        assert.Equal(t, 2, len(result.PerSecurity))
        
        // Find and verify each security's profit
        for _, sec := range result.PerSecurity {
            if sec.Ticker == "AAPL" {
                assert.Equal(t, 340.0, sec.Profit)
            } else if sec.Ticker == "MSFT" {
                assert.Equal(t, -100.0, sec.Profit)
            }
        }
    }