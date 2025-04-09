package saga

import "sync"

type SagaPhase int

const (
	PhaseInit SagaPhase = iota
	PhaseBuyerReserved
	PhaseSellerReceived
	PhaseOwnershipRemoved
	PhaseOwnershipTransferred
	PhaseVerified
)

type OtcSagaState struct {
	UID   string
	Phase SagaPhase
}

type SagaStateManager struct {
	states map[string]*OtcSagaState
	mu     sync.Mutex
}

var StateManager = SagaStateManager{
	states: make(map[string]*OtcSagaState),
}

func (m *SagaStateManager) UpdatePhase(uid string, phase SagaPhase) {
	m.mu.Lock()
	defer m.mu.Unlock()

	if _, exists := m.states[uid]; !exists {
		m.states[uid] = &OtcSagaState{UID: uid}
	}
	m.states[uid].Phase = phase
}

func (m *SagaStateManager) GetPhase(uid string) (SagaPhase, bool) {
	m.mu.Lock()
	defer m.mu.Unlock()

	state, exists := m.states[uid]
	if !exists {
		return 0, false
	}
	return state.Phase, true
}

func (m *SagaStateManager) Remove(uid string) {
	m.mu.Lock()
	defer m.mu.Unlock()

	delete(m.states, uid)
}
