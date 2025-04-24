package broker

import (
	"errors"

	"banka1.com/dto"
	"banka1.com/types"
)

func GetCustomerById(id int64) (*CustomerResponse, error) {
	var m CustomerResponse
	err := sendAndRecieve("get-customer", id, &m)
	if err != nil {
		return nil, err
	}
	return &m, nil
}

func SendOTCTransactionInit(dto *types.OTCTransactionInitiationDTO) error {
	return sendReliable("init-otc", dto)
}

func SendOTCTransactionFailure(uid string, message string) error {
	dto := &types.OTCTransactionACKDTO{
		Uid:     uid,
		Failure: true,
		Message: message,
	}
	return sendReliable("otc-ack-banking", dto)
}

func SendOTCTransactionSuccess(uid string) error {
	dto := &types.OTCTransactionACKDTO{
		Uid:     uid,
		Failure: false,
		Message: "",
	}
	return sendReliable("otc-ack-banking", dto)
}

func SendOTCPremium(dto *dto.OTCPremiumFeeDTO) error {
	return sendReliable("otc-pay-premium", dto)
}

func SendOrderTransactionInit(dto *dto.OrderTransactionInitiationDTO) error {
	if conn == nil {
		return nil
	}

	var m *string
	err := sendAndRecieve("order-init", dto, &m)
	if err != nil {
		return err
	}

	if m == nil || *m == "null" {
		return nil
	}

	return errors.New(*m)
}

func SendTaxCollection(dto *dto.TaxCollectionDTO) error {
	if conn == nil {
		return nil
	}

	var m *string
	err := sendAndRecieve("collect-tax", dto, &m)
	if err != nil {
		return err
	}

	if m == nil || *m == "null" {
		return nil
	}

	return errors.New(*m)
}