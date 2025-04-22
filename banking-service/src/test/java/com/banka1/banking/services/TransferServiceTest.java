package com.banka1.banking.services;

import com.banka1.banking.dto.CustomerDTO;
import com.banka1.banking.dto.InternalTransferDTO;
import com.banka1.banking.dto.MoneyTransferDTO;
import com.banka1.banking.dto.NotificationDTO;
import com.banka1.banking.models.*;
import com.banka1.banking.models.Currency;
import com.banka1.banking.models.helper.AccountStatus;
import com.banka1.banking.models.helper.CurrencyType;
import com.banka1.banking.models.helper.TransferStatus;
import com.banka1.banking.models.helper.TransferType;
import com.banka1.banking.repository.AccountRepository;
import com.banka1.banking.repository.CurrencyRepository;
import com.banka1.banking.repository.TransactionRepository;
import com.banka1.banking.repository.TransferRepository;
import com.banka1.common.listener.MessageHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class TransferServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private CurrencyRepository currencyRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private MessageHelper messageHelper;

    @Mock
    private UserServiceCustomer userServiceCustomer;

    @Mock
    private OtpTokenService otpTokenService;

    @Mock
    private BankAccountUtils bankAccountUtils;

    @Mock
    private ExchangeService exchangeService;

    @Mock
    private LoanService loanService;

    @InjectMocks
    private TransferService transferService;

    private Account fromAccountUSD;
    private Account toAccount;
    private Transfer internalTransfer;
    private Transfer externalTransfer;
    private Transfer exchangeTransfer;
    private Transfer foreignTransfer;
    private Account fromAccountForeign;
    private Account toAccountForeign;
    private Account bankAccountUSD;
    private Account bankAccountEUR;
    private Account bankAccountRSD;
    private Currency usdCurrency;
    private Currency eurCurrency;
    private Currency rsdCurrency;

    private CustomerDTO customerDTO;
    private CustomerDTO customerDTO2;
    private Transfer pendingTransfer;
    private Company bankCompany;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(transferService, "destinationEmail", "email.queue");

        bankCompany = new Company();
        bankCompany.setName("Banka");

        // Setup test accounts
        fromAccountUSD = new Account();
        fromAccountUSD.setId(1L);
        fromAccountUSD.setOwnerID(100L);
        fromAccountUSD.setAccountNumber("123456789");
        fromAccountUSD.setBalance(1000.0);
        fromAccountUSD.setCurrencyType(CurrencyType.USD);

        toAccount = new Account();
        toAccount.setId(2L);
        toAccount.setOwnerID(200L); // Different owner
        toAccount.setAccountNumber("987654321");
        toAccount.setBalance(500.0);
        toAccount.setCurrencyType(CurrencyType.USD);

        fromAccountForeign = new Account();
        fromAccountForeign.setId(2L);
        fromAccountForeign.setOwnerID(100L);
        fromAccountForeign.setAccountNumber("223456789");
        fromAccountForeign.setBalance(1000.0);
        fromAccountForeign.setCurrencyType(CurrencyType.EUR);

        toAccountForeign = new Account();
        toAccountForeign.setId(3L);
        toAccountForeign.setOwnerID(200L);
        toAccountForeign.setAccountNumber("987654322");
        toAccountForeign.setBalance(500.0);
        toAccountForeign.setCurrencyType(CurrencyType.EUR);

        bankAccountEUR = new Account();
        bankAccountEUR.setId(100L);
        bankAccountEUR.setOwnerID(1L);
        bankAccountEUR.setAccountNumber("111111111");
        bankAccountEUR.setBalance(1000000.0);
        bankAccountEUR.setCurrencyType(CurrencyType.EUR);
        bankAccountEUR.setCompany(bankCompany);

        bankAccountUSD = new Account();
        bankAccountUSD.setId(100L);
        bankAccountUSD.setOwnerID(1L);
        bankAccountUSD.setAccountNumber("111111112");
        bankAccountUSD.setBalance(1000000.0);
        bankAccountUSD.setCurrencyType(CurrencyType.USD);
        bankAccountUSD.setCompany(bankCompany);

        bankAccountRSD = new Account();
        bankAccountRSD.setId(100L);
        bankAccountRSD.setOwnerID(1L);
        bankAccountRSD.setAccountNumber("111111113");
        bankAccountRSD.setBalance(.0);
        bankAccountRSD.setCurrencyType(CurrencyType.RSD);
        bankAccountRSD.setCompany(bankCompany);


        // Setup currencies
        usdCurrency = new Currency();
        usdCurrency.setCode(CurrencyType.USD);

        eurCurrency = new Currency();
        eurCurrency.setCode(CurrencyType.EUR);

        rsdCurrency = new Currency();
        rsdCurrency.setCode(CurrencyType.RSD);

        // Setup customer data
        customerDTO = new CustomerDTO();
        customerDTO.setId(100L);
        customerDTO.setFirstName("John");
        customerDTO.setLastName("Doe");
        customerDTO.setEmail("john.doe@example.com");

        customerDTO2 = new CustomerDTO();
        customerDTO2.setId(200L);
        customerDTO2.setFirstName("Jane");
        customerDTO2.setLastName("Doe");
        customerDTO2.setEmail("jane.doe@example.com");

        // Setup pending transfer
        pendingTransfer = new Transfer();
        pendingTransfer.setId(1L);
        pendingTransfer.setFromAccountId(fromAccountUSD);
        pendingTransfer.setToAccountId(toAccount);
        pendingTransfer.setAmount(100.0);
        pendingTransfer.setStatus(TransferStatus.PENDING);
        pendingTransfer.setCreatedAt(System.currentTimeMillis() - 1000); // Created 1 second ago

        // Setup internal transfer
        internalTransfer = new Transfer();
        internalTransfer.setId(1L);
        internalTransfer.setFromAccountId(fromAccountUSD);
        internalTransfer.setToAccountId(toAccount);
        internalTransfer.setAmount(100.0);
        internalTransfer.setStatus(TransferStatus.PENDING);
        internalTransfer.setType(TransferType.INTERNAL);
        internalTransfer.setFromCurrency(usdCurrency);
        internalTransfer.setToCurrency(usdCurrency);

        // Setup external transfer
        externalTransfer = new Transfer();
        externalTransfer.setId(2L);
        externalTransfer.setFromAccountId(fromAccountUSD);
        externalTransfer.setToAccountId(toAccount);
        externalTransfer.setAmount(100.0);
        externalTransfer.setStatus(TransferStatus.PENDING);
        externalTransfer.setType(TransferType.EXTERNAL);
        externalTransfer.setFromCurrency(usdCurrency);
        externalTransfer.setToCurrency(usdCurrency);

        foreignTransfer = new Transfer();
        foreignTransfer.setId(3L);
        foreignTransfer.setFromAccountId(fromAccountUSD);
        foreignTransfer.setToAccountId(toAccountForeign);
        foreignTransfer.setAmount(100.0);
        foreignTransfer.setStatus(TransferStatus.PENDING);
        foreignTransfer.setType(TransferType.FOREIGN);
        foreignTransfer.setFromCurrency(usdCurrency);
        foreignTransfer.setToCurrency(eurCurrency);

        exchangeTransfer = new Transfer();
        exchangeTransfer.setId(4L);
        exchangeTransfer.setFromAccountId(fromAccountForeign);
        exchangeTransfer.setToAccountId(fromAccountUSD);
        exchangeTransfer.setAmount(100.0);
        exchangeTransfer.setStatus(TransferStatus.PENDING);
        exchangeTransfer.setType(TransferType.EXCHANGE);
        exchangeTransfer.setFromCurrency(eurCurrency);
        exchangeTransfer.setToCurrency(usdCurrency);
    }

    @Test
    void testCreateInternalTransfer_Success() {
        toAccount.setOwnerID(100L);

        InternalTransferDTO dto = new InternalTransferDTO();
        dto.setFromAccountId(fromAccountUSD.getId());
        dto.setToAccountId(toAccount.getId());
        dto.setAmount(100.0);

        // Setup mocks
        when(accountRepository.findById(fromAccountUSD.getId())).thenReturn(Optional.of(fromAccountUSD));
        when(accountRepository.findById(toAccount.getId())).thenReturn(Optional.of(toAccount));
        when(currencyRepository.findByCode(CurrencyType.USD)).thenReturn(Optional.of(usdCurrency));
        when(userServiceCustomer.getCustomerById(100L)).thenReturn(customerDTO);
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(otpTokenService.generateOtp(1L)).thenReturn("123456");

        // Execute
        Long result = transferService.createInternalTransfer(dto);

        // Verify
        assertEquals(1L, result);

        // Verify transfer was created and saved
        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).saveAndFlush(transferCaptor.capture());

        Transfer savedTransfer = transferCaptor.getValue();
        assertEquals(fromAccountUSD, savedTransfer.getFromAccountId());
        assertEquals(toAccount, savedTransfer.getToAccountId());
        assertEquals(100.0, savedTransfer.getAmount());
        assertEquals(TransferStatus.PENDING, savedTransfer.getStatus());
        assertEquals(usdCurrency, savedTransfer.getFromCurrency());
        assertEquals(usdCurrency, savedTransfer.getToCurrency());

        // Verify OTP was generated and set
        verify(otpTokenService).generateOtp(1L);
        verify(transferRepository).save(transferCaptor.capture());
        assertEquals("123456", transferCaptor.getValue().getOtp());

        // Verify notification was sent
        ArgumentCaptor<NotificationDTO> notificationCaptor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(messageHelper, times(2)).createTextMessage(notificationCaptor.capture());

        NotificationDTO sentNotification = notificationCaptor.getValue();
        assertEquals("Verifikacija", sentNotification.getSubject());
        assertEquals("john.doe@example.com", sentNotification.getEmail());
        assertTrue(sentNotification.getMessage().contains("Kliknite"));
        assertEquals("John", sentNotification.getFirstName());
        assertEquals("Doe", sentNotification.getLastName());
    }

    @Test
    void testCreateMoneyTransfer_ExternalTransfer_Success() {
        // Setup test data
        MoneyTransferDTO dto = new MoneyTransferDTO();
        dto.setFromAccountNumber("123456789");
        dto.setRecipientAccount("987654321");
        dto.setAmount(100.0);
        dto.setReceiver("Jane Smith");
        dto.setPayementCode("123");
        dto.setPayementDescription("Payment for services");

        // Setup mocks
        when(accountRepository.findByAccountNumber("123456789")).thenReturn(Optional.of(fromAccountUSD));
        when(accountRepository.findByAccountNumber("987654321")).thenReturn(Optional.of(toAccount));
        when(currencyRepository.findByCode(CurrencyType.USD)).thenReturn(Optional.of(usdCurrency));
        when(userServiceCustomer.getCustomerById(100L)).thenReturn(customerDTO);
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(otpTokenService.generateOtp(1L)).thenReturn("123456");

        // Execute
        Long result = transferService.createMoneyTransfer(dto);

        // Verify
        assertEquals(1L, result);

        // Verify transfer was created and saved
        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).saveAndFlush(transferCaptor.capture());

        Transfer savedTransfer = transferCaptor.getValue();
        assertEquals(fromAccountUSD, savedTransfer.getFromAccountId());
        assertEquals(toAccount, savedTransfer.getToAccountId());
        assertEquals(100.0, savedTransfer.getAmount());
        assertEquals("Jane Smith", savedTransfer.getReceiver());
        assertEquals(TransferStatus.PENDING, savedTransfer.getStatus());
        assertEquals(usdCurrency, savedTransfer.getFromCurrency());
        assertEquals(usdCurrency, savedTransfer.getToCurrency());
        assertEquals("123", savedTransfer.getPaymentCode());
        assertEquals("Payment for services", savedTransfer.getPaymentDescription());

        // Verify OTP was generated and set
        verify(otpTokenService).generateOtp(1L);
        verify(transferRepository).save(transferCaptor.capture());
        assertEquals("123456", transferCaptor.getValue().getOtp());

        // Verify notification was sent
        ArgumentCaptor<NotificationDTO> notificationCaptor = ArgumentCaptor.forClass(NotificationDTO.class);
        verify(messageHelper, times(2)).createTextMessage(notificationCaptor.capture());

        NotificationDTO sentNotification = notificationCaptor.getValue();
        assertEquals("Verifikacija", sentNotification.getSubject());
        assertEquals("john.doe@example.com", sentNotification.getEmail());
        assertTrue(sentNotification.getMessage().contains("Kliknite"));
        assertEquals("John", sentNotification.getFirstName());
        assertEquals("Doe", sentNotification.getLastName());
    }

    @Test
    void testCreateMoneyTransfer_ForeignTransfer_Success() {
        // Change to account for foreign transfer
        toAccount.setCurrencyType(CurrencyType.EUR);

        MoneyTransferDTO dto = new MoneyTransferDTO();
        dto.setFromAccountNumber("123456789");
        dto.setRecipientAccount("987654321");
        dto.setAmount(100.0);
        dto.setReceiver("Jane Smith");
        dto.setPayementCode("123");
        dto.setPayementDescription("International payment");

        when(accountRepository.findByAccountNumber("123456789")).thenReturn(Optional.of(fromAccountUSD));
        when(accountRepository.findByAccountNumber("987654321")).thenReturn(Optional.of(toAccount));
        when(currencyRepository.findByCode(CurrencyType.USD)).thenReturn(Optional.of(usdCurrency));
        when(currencyRepository.findByCode(CurrencyType.EUR)).thenReturn(Optional.of(eurCurrency));
        when(userServiceCustomer.getCustomerById(100L)).thenReturn(customerDTO);
        when(transferRepository.saveAndFlush(any(Transfer.class))).thenAnswer(invocation -> {
            Transfer t = invocation.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(otpTokenService.generateOtp(1L)).thenReturn("123456");

        Long result = transferService.createMoneyTransfer(dto);

        assertEquals(1L, result);

        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).saveAndFlush(transferCaptor.capture());

        assertEquals(usdCurrency, transferCaptor.getValue().getFromCurrency());
        assertEquals(eurCurrency, transferCaptor.getValue().getToCurrency());
    }

    @Test
    void testCreateMoneyTransfer_AccountNotFound() {
        MoneyTransferDTO dto = new MoneyTransferDTO();
        dto.setFromAccountNumber("111111111"); // Non-existent account
        dto.setRecipientAccount("987654321");

        when(accountRepository.findByAccountNumber("111111111")).thenReturn(Optional.empty());
        when(accountRepository.findByAccountNumber("987654321")).thenReturn(Optional.of(toAccount));

        Long result = transferService.createMoneyTransfer(dto);

        assertNull(result);
        verify(transferRepository, never()).saveAndFlush(any(Transfer.class));
    }

    @Test
    void testValidateMoneyTransfer_Success() {
        MoneyTransferDTO dto = new MoneyTransferDTO();
        dto.setFromAccountNumber("123456789");
        dto.setRecipientAccount("987654321");
        dto.setAmount(2e4);

        toAccount.setStatus(AccountStatus.ACTIVE);
        fromAccountUSD.setStatus(AccountStatus.ACTIVE);

        when(accountRepository.findByAccountNumber("123456789")).thenReturn(Optional.of(fromAccountUSD));
        when(accountRepository.findByAccountNumber("987654321")).thenReturn(Optional.of(toAccount));

        boolean result = transferService.validateMoneyTransfer(dto);

        assertTrue(result);
    }

    @Test
    void testValidateMoneyTransfer_SameOwner() {
        // Set same owner for both accounts
        toAccount.setOwnerID(100L);

        MoneyTransferDTO dto = new MoneyTransferDTO();
        dto.setFromAccountNumber("123456789");
        dto.setRecipientAccount("987654321");
        dto.setAmount(2e4);

        when(accountRepository.findByAccountNumber("123456789")).thenReturn(Optional.of(fromAccountUSD));
        when(accountRepository.findByAccountNumber("987654321")).thenReturn(Optional.of(toAccount));

        boolean result = transferService.validateMoneyTransfer(dto);

        // Should fail because accounts belong to same owner
        assertFalse(result);
    }

    @Test
    void testValidateInternalTransfer_Success() {
        // Set same owner for both accounts for internal transfer
        toAccount.setOwnerID(100L);
        toAccount.setStatus(AccountStatus.ACTIVE);
        fromAccountUSD.setStatus(AccountStatus.ACTIVE);

        InternalTransferDTO dto = new InternalTransferDTO();
        dto.setFromAccountId(1L);
        dto.setToAccountId(2L);
        dto.setAmount(100.0);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(fromAccountUSD));
        when(accountRepository.findById(2L)).thenReturn(Optional.of(toAccount));

        boolean result = transferService.validateInternalTransfer(dto);

        assertTrue(result);
    }

    @Test
    void testCancelExpiredTransfers() {
        // Create a list of expired transfers
        List<Transfer> expiredTransfers = Arrays.asList(pendingTransfer);

        // Setup mock to return expired transfers
        when(transferRepository.findAllByStatusAndCreatedAtBefore(
                eq(TransferStatus.PENDING), anyLong())).thenReturn(expiredTransfers);

        // Execute
        transferService.cancelExpiredTransfers();

        // Verify
        ArgumentCaptor<Transfer> transferCaptor = ArgumentCaptor.forClass(Transfer.class);
        verify(transferRepository).save(transferCaptor.capture());

        assertEquals(TransferStatus.CANCELLED, transferCaptor.getValue().getStatus());
    }

    @Test
    void testFindById_Success() {
        when(transferRepository.findById(1L)).thenReturn(Optional.of(pendingTransfer));

        Transfer result = transferService.findById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void testFindById_NotFound() {
        when(transferRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> transferService.findById(999L));
    }

    @Test
    void testProcessTransfer_Internal() {
        when(transferRepository.findById(1L)).thenReturn(Optional.of(internalTransfer));
        when(accountRepository.save(any(Account.class))).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(null);

        String result = transferService.processTransfer(1L);

        assertEquals("Transfer completed successfully", result);
        assertEquals(TransferStatus.COMPLETED, internalTransfer.getStatus());
        assertEquals(900.0, fromAccountUSD.getBalance());
        assertEquals(600.0, toAccount.getBalance());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void testProcessTransfer_Exchange() {
        when(transferRepository.findById(4L)).thenReturn(Optional.of(exchangeTransfer));
        when(accountRepository.save(any(Account.class))).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(null);
        when(userServiceCustomer.getCustomerById(100L)).thenReturn(customerDTO);

        when(exchangeService.calculatePreviewExchangeAutomatic(eq("EUR"), eq("RSD"), any())).thenReturn(
                Map.of(
                        "finalAmount", 11600.0,
                        "provision", 150.0
                )
        );
        when(exchangeService.calculatePreviewExchangeAutomatic(eq("RSD"), eq("USD"), any())).thenReturn(
                Map.of(
                        "finalAmount", 105.6,
                        "provision", 116.0
                )
        );

        when(currencyRepository.getByCode(CurrencyType.RSD)).thenReturn(rsdCurrency);
        when(currencyRepository.getByCode(CurrencyType.EUR)).thenReturn(eurCurrency);
        when(currencyRepository.getByCode(CurrencyType.USD)).thenReturn(usdCurrency);

        when(bankAccountUtils.getBankAccountForCurrency(eurCurrency.getCode())).thenReturn(bankAccountEUR);
        when(bankAccountUtils.getBankAccountForCurrency(usdCurrency.getCode())).thenReturn(bankAccountUSD);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(bankAccountRSD);

        String result = transferService.processTransfer(4L);

        assertEquals("Transfer completed successfully", result);
        assertEquals(TransferStatus.COMPLETED, exchangeTransfer.getStatus());
        assertEquals(900.0, fromAccountForeign.getBalance());
        assertEquals(1105.6, fromAccountUSD.getBalance());
        assertTrue(1000000.0 < bankAccountEUR.getBalance());
        assertTrue(999900.0 > bankAccountUSD.getBalance());
        verify(currencyRepository, times(3)).getByCode(any(CurrencyType.class));
    }

    @Test
    void testProcessTransfer_External() {
        when(transferRepository.findById(2L)).thenReturn(Optional.of(externalTransfer));
        when(accountRepository.save(any(Account.class))).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(null);

        String result = transferService.processTransfer(2L);

        assertEquals("Transfer completed successfully", result);
        assertEquals(TransferStatus.COMPLETED, externalTransfer.getStatus());
        assertEquals(900.0, fromAccountUSD.getBalance());
        assertEquals(600.0, toAccount.getBalance());
        verify(transactionRepository, times(1)).save(any(Transaction.class));
    }

    @Test
    void testProcessTransfer_Foreign() {
        when(transferRepository.findById(3L)).thenReturn(Optional.of(foreignTransfer));
        when(accountRepository.save(any(Account.class))).thenReturn(null);
        when(transactionRepository.save(any(Transaction.class))).thenReturn(null);
        when(userServiceCustomer.getCustomerById(200L)).thenReturn(customerDTO2);

        when(exchangeService.calculatePreviewExchangeAutomatic(anyString(), anyString(), any())).thenReturn(
                Map.of(
                        "finalAmount", 90.0,
                        "provision", 100.0
                )
        );

        when(currencyRepository.getByCode(CurrencyType.RSD)).thenReturn(rsdCurrency);
        when(currencyRepository.getByCode(CurrencyType.EUR)).thenReturn(eurCurrency);
        when(currencyRepository.getByCode(CurrencyType.USD)).thenReturn(usdCurrency);

        when(bankAccountUtils.getBankAccountForCurrency(eurCurrency.getCode())).thenReturn(bankAccountEUR);
        when(bankAccountUtils.getBankAccountForCurrency(usdCurrency.getCode())).thenReturn(bankAccountUSD);
        when(bankAccountUtils.getBankAccountForCurrency(rsdCurrency.getCode())).thenReturn(bankAccountRSD);


        String result = transferService.processTransfer(3L);

        assertEquals("Transfer completed successfully", result);
        assertEquals(TransferStatus.COMPLETED, foreignTransfer.getStatus());
        assertEquals(800.0, fromAccountUSD.getBalance());
        assertEquals(590.0, toAccountForeign.getBalance());
        assertEquals(1000100.0, bankAccountUSD.getBalance());
        assertEquals(999910.0, bankAccountEUR.getBalance());
    }

    @Test
    void testProcessInternalTransfer_InsufficientFunds() {
        fromAccountUSD.setBalance(50.0);
        internalTransfer.setAmount(100.0);

        when(transferRepository.findById(1L)).thenReturn(Optional.of(internalTransfer));

        assertThrows(RuntimeException.class, () -> transferService.processInternalTransfer(1L));
        assertEquals(TransferStatus.FAILED, internalTransfer.getStatus());
    }

    @Test
    void testProcessExternalTransfer_InsufficientFunds() {
        fromAccountUSD.setBalance(50.0);
        externalTransfer.setAmount(100.0);

        when(transferRepository.findById(2L)).thenReturn(Optional.of(externalTransfer));

        assertThrows(RuntimeException.class, () -> transferService.processExternalTransfer(2L));
        assertEquals(TransferStatus.FAILED, externalTransfer.getStatus());
    }

    @Test
    public void testPerformRsdToForeign_successfulExchange() {

        Double amount = 1000.0;

        Account fromAccount = new Account();
        fromAccount.setCurrencyType(CurrencyType.RSD);
        fromAccount.setBalance(5000.0);

        Account toAccount = new Account();
        toAccount.setCurrencyType(CurrencyType.EUR);
        toAccount.setBalance(200.0);
        toAccount.setOwnerID(1L);

        Currency rsdCurrency = new Currency();
        rsdCurrency.setCode(CurrencyType.RSD);
        Currency eurCurrency = new Currency();
        rsdCurrency.setCode(CurrencyType.EUR);

        Account rsdBankAccount = new Account();
        rsdBankAccount.setBalance(100000.0);
        rsdBankAccount.setCurrencyType(CurrencyType.RSD);
        rsdBankAccount.setCompany(new Company());

        Account eurBankAccount = new Account();
        eurBankAccount.setBalance(100000.0);
        eurBankAccount.setCurrencyType(CurrencyType.EUR);
        eurBankAccount.setCompany(new Company());

        CustomerDTO customer = new CustomerDTO();
        customer.setFirstName("John");
        customer.setLastName("Doe");

        Map<String, Object> exchangeMock = new HashMap<>();
        exchangeMock.put("finalAmount", 8.5);
        exchangeMock.put("provision", 0.5);

        when(currencyRepository.getByCode(CurrencyType.RSD)).thenReturn(rsdCurrency);
        when(currencyRepository.getByCode(CurrencyType.EUR)).thenReturn(eurCurrency);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(rsdBankAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.EUR)).thenReturn(eurBankAccount);
        when(userServiceCustomer.getCustomerById(1L)).thenReturn(customer);
        when(exchangeService.calculatePreviewExchangeAutomatic("RSD", "EUR", amount)).thenReturn(exchangeMock);


        Map<String, Object> result = transferService.performRsdToForeign(amount, fromAccount, toAccount);


        assertNotNull(result);
        assertEquals(8.5, result.get("finalAmount"));
        assertEquals(0.5, result.get("provision"));

        assertEquals(4000.0, fromAccount.getBalance());
        assertEquals(208.5, toAccount.getBalance());
        assertEquals(100000.0, rsdBankAccount.getBalance());
        assertEquals(100991.5, eurBankAccount.getBalance());
    }

    @Test
    public void testPerformForeignToRsd_successfulExchange() {
        // Arrange
        Double amount = 100.0;

        Account fromAccount = new Account();
        fromAccount.setCurrencyType(CurrencyType.EUR);
        fromAccount.setBalance(1000.0);

        Account toAccount = new Account();
        toAccount.setCurrencyType(CurrencyType.RSD);
        toAccount.setBalance(10000.0);
        toAccount.setOwnerID(1L);

        Currency eurCurrency = new Currency();
        eurCurrency.setCode(CurrencyType.EUR);
        Currency rsdCurrency = new Currency();
        rsdCurrency.setCode(CurrencyType.RSD);

        Account eurBankAccount = new Account();
        eurBankAccount.setCurrencyType(CurrencyType.EUR);
        eurBankAccount.setBalance(100000.0);
        eurBankAccount.setCompany(new Company());

        Account rsdBankAccount = new Account();
        rsdBankAccount.setCurrencyType(CurrencyType.RSD);
        rsdBankAccount.setBalance(200000.0);
        rsdBankAccount.setCompany(new Company());

        CustomerDTO customer = new CustomerDTO();
        customer.setFirstName("Ana");
        customer.setLastName("Markovic");

        Map<String, Object> exchangeMock = new HashMap<>();
        exchangeMock.put("finalAmount", 11700.0);
        exchangeMock.put("provision", 100.0);

        when(currencyRepository.getByCode(CurrencyType.RSD)).thenReturn(rsdCurrency);
        when(currencyRepository.getByCode(CurrencyType.EUR)).thenReturn(eurCurrency);
        when(userServiceCustomer.getCustomerById(1L)).thenReturn(customer);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(rsdBankAccount);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.EUR)).thenReturn(eurBankAccount);
        when(exchangeService.calculatePreviewExchangeAutomatic("EUR", "RSD", amount)).thenReturn(exchangeMock);

        // Act
        Map<String, Object> result = transferService.performForeignToRsd(amount, fromAccount, toAccount);

        // Assert
        assertNotNull(result);
        assertEquals(11700.0, result.get("finalAmount"));
        assertEquals(100.0, result.get("provision"));

        assertEquals(900.0, fromAccount.getBalance());
        assertEquals(21700.0, toAccount.getBalance());
        assertEquals(100100.0, eurBankAccount.getBalance());
        assertEquals(188300.0, rsdBankAccount.getBalance());
    }
}