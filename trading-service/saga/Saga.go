package saga

import (
	"errors"
	"sync"

	"banka1.com/types"
	"gorm.io/gorm"
)

type SagaStateManager struct {
	mus map[string]*sync.Mutex
	mu  sync.Mutex
}

var StateManager = SagaStateManager{
	mus: make(map[string]*sync.Mutex),
}

func (m *SagaStateManager) lockUID(uid string) *sync.Mutex {
	mu, exists := m.mus[uid]
	if !exists {
		mu = &sync.Mutex{}
		m.mus[uid] = mu
	}
	mu.Lock()
	return mu
}

func (m *SagaStateManager) LockUID(uid string) *sync.Mutex {
	m.mu.Lock()
	defer m.mu.Unlock()
	return m.lockUID(uid)
}

func (m *SagaStateManager) UpdatePhase(tx *gorm.DB, uid string, phase types.OTCSagaPhase) error {
	mu := m.LockUID(uid)
	defer mu.Unlock()

	var state types.OTCSagaState

	if err := tx.First(&state, "uid = ?", uid).Error; err != nil {
		if !errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		}
		state.UID = uid
	}

	state.Phase = phase

	return tx.Save(&state).Error
}

func (m *SagaStateManager) GetPhase(tx *gorm.DB, uid string) (types.OTCSagaPhase, bool, error) {
	mu := m.LockUID(uid)
	defer mu.Unlock()

	var state types.OTCSagaState

	if err := tx.First(&state, "uid = ?", uid).Error; err != nil {
		if errors.Is(err, gorm.ErrRecordNotFound) {
			return 0, false, nil
		}
		return 0, false, err
	}

	return state.Phase, true, nil
}

func (m *SagaStateManager) Remove(tx *gorm.DB, uid string) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	mu := m.lockUID(uid)
	defer mu.Unlock()

	var state types.OTCSagaState

	err := tx.First(&state, "uid = ?", uid).Error

	if err != nil && !errors.Is(err, gorm.ErrRecordNotFound) {
		return err
	}

	if err == nil {
		if err := tx.Delete(&state).Error; err != nil {
			return err
		}
	}

	delete(m.mus, uid)

	return err
}
