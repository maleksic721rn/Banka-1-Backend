package com.banka1.banking.services;

import com.banka1.banking.dto.MoneyTransferDTO;
import com.banka1.banking.models.Account;
import com.banka1.banking.models.helper.CurrencyType;
import com.banka1.banking.repository.AccountRepository;
import com.banka1.banking.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private AccountService accountService;
    @Mock
    private BankAccountUtils bankAccountUtils;
    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransferService transferService;

    @InjectMocks
    private OrderService orderService;

    private Account userAccount;
    private Account bankAccount;

    @BeforeEach
    void setUp() {
        userAccount = new Account();
        userAccount.setId(1L);
        userAccount.setOwnerID(10L);
        userAccount.setAccountNumber("123-456");
        userAccount.setBalance(100000.0);
        userAccount.setCurrencyType(CurrencyType.RSD);

        bankAccount = new Account();
        bankAccount.setId(2L);
        bankAccount.setOwnerID(1L);
        bankAccount.setAccountNumber("000-111");
        bankAccount.setBalance(1000000.0);
        bankAccount.setCurrencyType(CurrencyType.RSD);
    }

    @Test
    void testExecuteOrder_TransferHappens_BankIsNotSameAccount() {
        when(accountService.findById(1L)).thenReturn(userAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(bankAccount);

        double result = orderService.executeOrder("buy", 10L, 1L, 50000.0, 100.0);

        assertEquals(50000.0, result);

        ArgumentCaptor<MoneyTransferDTO> captor = ArgumentCaptor.forClass(MoneyTransferDTO.class);
        verify(transferService, times(2)).createMoneyTransfer(captor.capture());

        List<MoneyTransferDTO> calls = captor.getAllValues();

        MoneyTransferDTO mainTransfer = calls.get(0);
        assertEquals("123-456", mainTransfer.getFromAccountNumber());
        assertEquals("000-111", mainTransfer.getRecipientAccount());
        assertEquals(50000.0, mainTransfer.getAmount());

        MoneyTransferDTO feeTransfer = calls.get(1);
        assertEquals("123-456", feeTransfer.getFromAccountNumber());
        assertEquals("000-111", feeTransfer.getRecipientAccount());
        assertEquals(100.0, feeTransfer.getAmount());
        assertEquals("Fee", feeTransfer.getPayementReference());
    }

    @Test
    void testExecuteOrder_FeeTransfer_TriggeredSeparately() {
        when(accountService.findById(1L)).thenReturn(userAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(bankAccount);

        orderService.executeOrder("buy", 10L, 1L, 1000.0, 200.0);

        ArgumentCaptor<MoneyTransferDTO> captor = ArgumentCaptor.forClass(MoneyTransferDTO.class);
        verify(transferService, times(2)).createMoneyTransfer(captor.capture());

        MoneyTransferDTO feeTransfer = captor.getAllValues().get(1);
        assertEquals("123-456", feeTransfer.getFromAccountNumber());
        assertEquals("000-111", feeTransfer.getRecipientAccount());
        assertEquals(200.0, feeTransfer.getAmount());
        assertEquals("Fee", feeTransfer.getPayementReference());
    }

    @Test
    void testExecuteOrder_ForeignCurrency_CallsTransfer() {
        userAccount.setCurrencyType(CurrencyType.USD);
        bankAccount.setCurrencyType(CurrencyType.EUR);

        when(accountService.findById(1L)).thenReturn(userAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.USD)).thenReturn(bankAccount);

        double result = orderService.executeOrder("buy", 10L, 1L, 50000.0, 100.0);

        assertEquals(50000.0, result);
        verify(transferService, times(2)).createMoneyTransfer(any());
    }

    @Test
    void testExecuteOrder_InvalidUser_ThrowsException() {
        userAccount.setOwnerID(99L);

        when(accountService.findById(1L)).thenReturn(userAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(bankAccount);

        assertThrows(RuntimeException.class, () ->
                orderService.executeOrder("buy", 10L, 1L, 1000.0, 0.0)
        );
    }

    @Test
    void testExecuteOrder_InsufficientFunds_ThrowsException() {
        userAccount.setBalance(100.0);

        when(accountService.findById(1L)).thenReturn(userAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(bankAccount);

        assertThrows(IllegalArgumentException.class, () ->
                orderService.executeOrder("buy", 10L, 1L, 500.0, 100.0)
        );
    }

    @Test
    void testExecuteOrder_SameAccount_UpdatesBalanceDirectly() {
        when(accountService.findById(1L)).thenReturn(userAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(userAccount);

        double result = orderService.executeOrder("buy", 10L, 1L, 1000.0, 100.0);

        assertEquals(1000.0, result);
        assertEquals(98900.0, userAccount.getBalance());
        verify(accountRepository).save(userAccount);
        verifyNoInteractions(transferService);
    }
    @Test
    void testExecuteOrder_RollbackIfTransferFails() {
        when(accountService.findById(1L)).thenReturn(userAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(bankAccount);

        doThrow(new RuntimeException("Greška u transferu"))
                .when(transferService).createMoneyTransfer(any());

        double originalBalance = userAccount.getBalance();

        assertThrows(RuntimeException.class, () ->
                orderService.executeOrder("buy", 10L, 1L, 1000.0, 100.0)
        );

        assertEquals(originalBalance, userAccount.getBalance());

        verify(accountRepository, never()).save(userAccount);
    }

}
