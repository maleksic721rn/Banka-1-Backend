package cron

import (
	"banka1.com/types"
	"testing"

	_ "github.com/mattn/go-sqlite3"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"
	"gorm.io/driver/sqlite"
	"gorm.io/gorm"
)

// MockDBWrapper helps us mock the database interactions
type MockDB struct {
	mock.Mock
}

func (m *MockDB) Find(dest interface{}, conds ...interface{}) *MockDB {
	args := m.Called(dest)
	return args.Get(0).(*MockDB)
}

func (m *MockDB) Where(query interface{}, args ...interface{}) *MockDB {
	m.Called(query, args)
	return m
}

func (m *MockDB) First(dest interface{}) *MockDB {
	args := m.Called(dest)
	return args.Get(0).(*MockDB)
}

func (m *MockDB) Create(value interface{}) *MockDB {
	args := m.Called(value)
	return args.Get(0).(*MockDB)
}

func (m *MockDB) Save(value interface{}) *MockDB {
	args := m.Called(value)
	return args.Get(0).(*MockDB)
}

func (m *MockDB) Update(column string, value interface{}) *MockDB {
	args := m.Called(column, value)
	return args.Get(0).(*MockDB)
}

func (m *MockDB) Model(value interface{}) *MockDB {
	args := m.Called(value)
	return args.Get(0).(*MockDB)
}

func (m *MockDB) Error() error {
	args := m.Called()
	return args.Error(0)
}

// Setup an in-memory database for testing
func setupTestDB() *gorm.DB {
	// Use a file-based in-memory database with the pure Go driver
	testDB, err := gorm.Open(sqlite.Open(":memory:"), &gorm.Config{})
	if err != nil {
		panic("Failed to open in-memory database: " + err.Error())
	}

	// Auto migrate the necessary models
	testDB.AutoMigrate(&types.Listing{})
	testDB.AutoMigrate(&types.ListingHistory{})
	testDB.AutoMigrate(&types.OptionContract{})
	testDB.AutoMigrate(&types.Actuary{})

	return testDB
}

// TestEmployeeToActuary tests the employeeToActuary function
func TestEmployeeToActuary(t *testing.T) {
	// Setup
	employee := Employee{
		ID:          123,
		FirstName:   "Alice",
		LastName:    "Smith",
		Email:       "alice.smith@example.com",
		Department:  "Risk",
		Position:    "Analyst",
		Active:      true,
		Permissions: []string{"trade", "view"},
	}

	// Execute
	actuary := employeeToActuary(employee)

	// Verify
	assert.Equal(t, uint(123), actuary.UserID)
	assert.Equal(t, "Risk", actuary.Department)
	assert.Equal(t, "Alice Smith", actuary.FullName)
	assert.Equal(t, "alice.smith@example.com", actuary.Email)
	assert.Equal(t, float64(100000), actuary.LimitAmount)
}
