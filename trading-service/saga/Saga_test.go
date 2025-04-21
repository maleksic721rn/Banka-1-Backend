package saga

import (
	"sync"
	"testing"

	"banka1.com/db"
	"banka1.com/types"

	"github.com/stretchr/testify/assert"
)

func TestGetUpdatePhase(t *testing.T) {
	// Create a new state manager for testing
	manager := SagaStateManager{
		mus: make(map[string]*sync.Mutex),
	}

	err := db.InitTestDatabase()
	assert.NoError(t, err)

	// Test getting a non-existent phase
	uid := "test-uid-2"
	phase, exists, err := manager.GetPhase(db.DB, uid)
	assert.NoError(t, err)

	if exists {
		t.Errorf("Expected exists to be false for non-existent UID, got true")
	}

	if phase != 0 {
		t.Errorf("Expected default phase 0 for non-existent UID, got %d", phase)
	}

	// Test creating a new entry
	err = manager.UpdatePhase(db.DB, uid, types.PhaseOwnershipTransferred)
	assert.NoError(t, err)

	phase, exists, err = manager.GetPhase(db.DB, uid)
	assert.NoError(t, err)

	if !exists {
		t.Errorf("Expected exists to be true for existing UID, got false")
	}

	if phase != types.PhaseOwnershipTransferred {
		t.Errorf("Expected phase %d, got %d", types.PhaseOwnershipTransferred, phase)
	}

	// Test updating an existing entry
	err = manager.UpdatePhase(db.DB, uid, types.PhaseSellerReceived)
	assert.NoError(t, err)

	phase, exists, err = manager.GetPhase(db.DB, uid)
	assert.NoError(t, err)

	if !exists {
		t.Errorf("Expected exists to be true for existing UID, got false")
	}

	if phase != types.PhaseSellerReceived {
		t.Errorf("Expected phase %d, got %d", types.PhaseSellerReceived, phase)
	}
}

func TestRemove(t *testing.T) {
	// Create a new state manager for testing
	manager := SagaStateManager{
		mus: make(map[string]*sync.Mutex),
	}

	err := db.InitTestDatabase()
	assert.NoError(t, err)

	// Set up test data
	uid := "test-uid-3"
	err = manager.UpdatePhase(db.DB, uid, types.PhaseVerified)
	assert.NoError(t, err)

	// Test removing an existing entry
	err = manager.Remove(db.DB, uid)
	assert.NoError(t, err)

	_, exists, err := manager.GetPhase(db.DB, uid)
	assert.NoError(t, err)

	if exists {
		t.Errorf("Expected state to be removed for UID %s, but it still exists", uid)
	}

	// Test removing a non-existent entry (should not panic)
	nonExistentUID := "non-existent"
	err = manager.Remove(db.DB, nonExistentUID) // This should not panic
	assert.Error(t, err)
}

func TestConcurrentOperations(t *testing.T) {
	// This test checks that the mutex prevents race conditions
	manager := SagaStateManager{
		mus: make(map[string]*sync.Mutex),
	}

	uid := "test-uid-4"

	// Run operations concurrently
	done := make(chan bool)

	err := db.InitTestDatabase()
	assert.NoError(t, err)

	go func() {
		for i := 0; i < 100; i++ {
			err := manager.UpdatePhase(db.DB, uid, types.OTCSagaPhase(i%6))
			assert.NoError(t, err)
		}
		done <- true
	}()

	go func() {
		for i := 0; i < 100; i++ {
			_, _, err := manager.GetPhase(db.DB, uid)
			assert.NoError(t, err)
		}
		done <- true
	}()

	go func() {
		for i := 0; i < 100; i++ {
			if i%10 == 0 {
				manager.Remove(db.DB, uid)
			}
		}
		done <- true
	}()

	// Wait for all goroutines to finish
	<-done
	<-done
	<-done

	// If we got here without deadlock or panic, the test passes
}

func TestPhaseConstants(t *testing.T) {
	// Verify the phase constants have expected values
	phases := []types.OTCSagaPhase{
		types.PhaseInit,
		types.PhaseBuyerReserved,
		types.PhaseSellerReceived,
		types.PhaseOwnershipRemoved,
		types.PhaseOwnershipTransferred,
		types.PhaseVerified,
	}

	for i, phase := range phases {
		if int(phase) != i {
			t.Errorf("Expected phase at index %d to have value %d, got %d", i, i, phase)
		}
	}
}
