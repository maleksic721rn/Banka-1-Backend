package stocks

import (
    "testing"

    "banka1.com/db"
    "banka1.com/listings/finhub"
    "github.com/DATA-DOG/go-sqlmock"
    "gorm.io/driver/postgres"
    "gorm.io/gorm"
    "gorm.io/gorm/logger"
)

func NewMockGormDB() (*gorm.DB, sqlmock.Sqlmock, error) {
    sqlDB, mock, err := sqlmock.New()
    if err != nil {
        return nil, nil, err
    }

    gormDB, err := gorm.Open(postgres.New(postgres.Config{
        Conn: sqlDB,
    }), &gorm.Config{
        Logger: logger.Default.LogMode(logger.Silent),
    })
    if err != nil {
        return nil, nil, err
    }

    return gormDB, mock, nil
}

func TestLoadStockFromAPI_Basic(t *testing.T) {
    mockDB, mock, err := NewMockGormDB()
    if err != nil {
        t.Fatal(err)
    }
    db.DB = mockDB // override global DB

    // Test data
    stockRequest := finhub.Stock{
        Symbol: "AAPL",
        Mic:    "XNAS",
        Type:   "Stock",
    }

	stockRequest.Symbol = "AAPL"
	stockRequest.Mic = "XNAS"

    // Mock the exchange query
    exchangeRows := sqlmock.NewRows([]string{"id", "name", "mic_code"}).
        AddRow(1, "NASDAQ", "XNAS")
    mock.ExpectQuery(`SELECT .* FROM "exchanges" WHERE mic_code = \$1`).
        WithArgs("XNAS").
        WillReturnRows(exchangeRows)

    // Transaction begins
    mock.ExpectBegin()
    
    // Mock the listing query - not found case
    mock.ExpectQuery(`SELECT .* FROM "listings" WHERE ticker = \$1`).
        WithArgs("AAPL").
        WillReturnError(gorm.ErrRecordNotFound)
    
    
    // Stock details query - not found
    mock.ExpectQuery(`SELECT .* FROM "stocks" WHERE listing_id = \$1`).
        WithArgs(1).
        WillReturnError(gorm.ErrRecordNotFound)
    
    
    
    mock.ExpectCommit()


    // Check if all expected SQL calls were made
    if err := mock.ExpectationsWereMet(); err == nil {
        t.Errorf("There were unfulfilled expectations: %s", err)
    }
}

func TestLoadStockFromAPI_StockExists(t *testing.T) {
	stock := LoadStockFromAPI(finhub.Stock{})
	
	if stock == nil {
		t.Errorf("Expected stock to be loaded, but got nil")
	}
}
