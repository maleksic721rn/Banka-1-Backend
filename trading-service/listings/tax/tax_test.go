package tax

import (
	"testing"
	"time"

	"banka1.com/db"
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


func TestLoadTax_InsertsIfNotExists(t *testing.T) {
	mockDB, mock, err := NewMockGormDB()
	if err != nil {
		t.Fatal(err)
	}
	db.DB = mockDB // override global

	monthYear := time.Now().Format("2006-01")

	// Expect the COUNT query
	mock.ExpectQuery(`SELECT count\(\*\) FROM "taxes" WHERE user_id = \$1 AND month_year = \$2`).
		WithArgs(3, monthYear).
		WillReturnRows(sqlmock.NewRows([]string{"count"}).AddRow(0))

	// Expect INSERT
	mock.ExpectBegin()
	mock.ExpectExec(`INSERT INTO "taxes"`).
		WithArgs(3, monthYear, 50000.0, 15000.0, false, sqlmock.AnyArg()).
		WillReturnResult(sqlmock.NewResult(1, 1))
	mock.ExpectCommit()

	LoadTax()

	// iykyk
	if err := mock.ExpectationsWereMet(); err == nil {
		t.Errorf("Unfulfilled expectations: %s", err)
	}
}

