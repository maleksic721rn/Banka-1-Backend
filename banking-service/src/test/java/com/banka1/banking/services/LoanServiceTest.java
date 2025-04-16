package com.banka1.banking.services;

import com.banka1.banking.dto.request.CreateLoanDTO;
import com.banka1.banking.models.Account;
import com.banka1.banking.models.Installment;
import com.banka1.banking.models.Loan;
import com.banka1.banking.models.helper.CurrencyType;
import com.banka1.banking.models.helper.InterestType;
import com.banka1.banking.models.helper.LoanType;
import com.banka1.banking.models.helper.PaymentStatus;
import com.banka1.banking.repository.AccountRepository;
import com.banka1.banking.repository.InstallmentsRepository;
import com.banka1.banking.repository.LoanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LoanServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private InstallmentsRepository installmentsRepository;

    @InjectMocks
    private LoanService loanService;

    private Account testAccount;
    private CreateLoanDTO createLoanDTO;
    private Loan savedLoan;
    private Installment installment;

    @BeforeEach
    public void setUp() {
        testAccount = new Account();
        testAccount.setId(1L);
        testAccount.setCurrencyType(CurrencyType.USD);
        testAccount.setOwnerID(1L);


        createLoanDTO = new CreateLoanDTO();
        createLoanDTO.setAccountId(1L);
        createLoanDTO.setLoanAmount(10000.0);
        createLoanDTO.setNumberOfInstallments(60);
        createLoanDTO.setLoanType(LoanType.CASH);
        createLoanDTO.setInterestType(InterestType.FIXED);
        createLoanDTO.setPhoneNumber("1234567890");
        createLoanDTO.setLoanPurpose("Home renovation");

        savedLoan = new Loan();
        savedLoan.setId(1L);
        savedLoan.setLoanType(LoanType.CASH);
        savedLoan.setNumberOfInstallments(12);
        savedLoan.setCurrencyType(CurrencyType.USD);
        savedLoan.setInterestType(InterestType.FIXED);
        savedLoan.setPaymentStatus(PaymentStatus.LATE);
        savedLoan.setNominalRate(5.5);
        savedLoan.setEffectiveRate(5.8);
        savedLoan.setPenalty(50.0);
        savedLoan.setLoanAmount(5000.0);
        savedLoan.setCreatedDate(System.currentTimeMillis());
        savedLoan.setAllowedDate(System.currentTimeMillis() + 1000000L);
        savedLoan.setMonthlyPayment(450.0);
        savedLoan.setNumberOfPaidInstallments(3);
        savedLoan.setNextPaymentDate(LocalDate.now().plusMonths(1));
        savedLoan.setRemainingAmount(4000.0);
        savedLoan.setPhoneNumber("+1234567890");
        savedLoan.setLoanReason("Car Purchase");
        savedLoan.setAccount(testAccount);


        installment = new Installment();
        installment.setId(1L);
        installment.setLoan(savedLoan);
        installment.setPaymentStatus(PaymentStatus.PENDING);
        installment.setAmount(500.0);
    }

    @Test
    public void testCreateLoan_Success() {

        Loan savedLoan = new Loan();
        savedLoan.setId(1L);
        savedLoan.setLoanType(LoanType.CASH);
        savedLoan.setNumberOfInstallments(12);
        savedLoan.setCurrencyType(CurrencyType.USD);
        savedLoan.setInterestType(InterestType.FIXED);
        savedLoan.setPaymentStatus(PaymentStatus.PENDING);
        savedLoan.setNominalRate(5.5);
        savedLoan.setEffectiveRate(5.8);
        savedLoan.setPenalty(50.0);
        savedLoan.setLoanAmount(5000.0);
        savedLoan.setCreatedDate(System.currentTimeMillis());
        savedLoan.setAllowedDate(System.currentTimeMillis() + 1000000L);
        savedLoan.setMonthlyPayment(450.0);
        savedLoan.setNumberOfPaidInstallments(3);
        savedLoan.setNextPaymentDate(LocalDate.now().plusMonths(1));
        savedLoan.setRemainingAmount(4000.0);
        savedLoan.setPhoneNumber("+1234567890");
        savedLoan.setLoanReason("Car Purchase");
        savedLoan.setAccount(null);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(loanRepository.save(any(Loan.class))).thenReturn(savedLoan);


        Loan result = loanService.createLoan(createLoanDTO);


        assertNotNull(result);
        assertEquals(1L, result.getId());

        verify(accountRepository).findById(1L);

    }

    @Test
    public void testCreateLoan_AccountNotFound() {
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> loanService.createLoan(createLoanDTO));
        verify(accountRepository).findById(1L);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_NegativeLoanAmount() {
        createLoanDTO.setLoanAmount(-500.0);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        assertThrows(RuntimeException.class, () -> loanService.createLoan(createLoanDTO));
        verify(accountRepository).findById(1L);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_ZeroLoanAmount() {
        createLoanDTO.setLoanAmount(0.0);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        assertThrows(RuntimeException.class, () -> loanService.createLoan(createLoanDTO));
        verify(accountRepository).findById(1L);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_MortgageValidInstallments() {

        createLoanDTO.setLoanType(LoanType.MORTGAGE);
        createLoanDTO.setNumberOfInstallments(120);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(loanRepository.save(any(Loan.class))).thenReturn(new Loan());

        Loan result = loanService.createLoan(createLoanDTO);


        assertNotNull(result);
        verify(loanRepository).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_MortgageInvalidInstallments() {
        // Arrange
        createLoanDTO.setLoanType(LoanType.MORTGAGE);
        createLoanDTO.setNumberOfInstallments(50); // Not divisible by 60, invalid for mortgage

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        // Act
        Loan result = loanService.createLoan(createLoanDTO);

        // Assert
        assertNull(result);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_MortgageTooManyInstallments() {
        // Arrange
        createLoanDTO.setLoanType(LoanType.MORTGAGE);
        createLoanDTO.setNumberOfInstallments(420);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        // Act
        Loan result = loanService.createLoan(createLoanDTO);

        // Assert
        assertNull(result);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_CashLoanValidInstallments() {

        createLoanDTO.setLoanType(LoanType.CASH);
        createLoanDTO.setNumberOfInstallments(36);


        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(loanRepository.save(any(Loan.class))).thenReturn(savedLoan);
        Loan loan = loanService.updateLoanRate(savedLoan, eq(false));


        Loan result = loanService.createLoan(createLoanDTO);


        assertNotNull(result);
        assertNotNull(loan);

    }

    @Test
    public void testCreateLoan_CashLoanInvalidInstallments() {

        createLoanDTO.setLoanType(LoanType.CASH);
        createLoanDTO.setNumberOfInstallments(15);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));


        Loan result = loanService.createLoan(createLoanDTO);


        assertNull(result);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_CashLoanTooManyInstallments() {

        createLoanDTO.setLoanType(LoanType.CASH);
        createLoanDTO.setNumberOfInstallments(96);

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));

        Loan result = loanService.createLoan(createLoanDTO);

        assertNull(result);
        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateLoan_VerifyLoanProperties() {

        Instant now = Instant.now();

        when(accountRepository.findById(1L)).thenReturn(Optional.of(testAccount));
        when(loanRepository.save(any(Loan.class))).thenAnswer(invocation -> {
            Loan savedLoan = invocation.getArgument(0);
            savedLoan.setId(1L);
            return savedLoan;
        });

        // Act
        Loan result = loanService.createLoan(createLoanDTO);

        // Assert
        assertNotNull(result);
        assertEquals(testAccount, result.getAccount());
        assertEquals(LoanType.CASH, result.getLoanType());
        assertEquals(60, result.getNumberOfInstallments());
        assertEquals(InterestType.FIXED, result.getInterestType());
        assertEquals(10000.0, result.getLoanAmount());
        assertEquals(CurrencyType.USD, result.getCurrencyType());
        assertEquals("1234567890", result.getPhoneNumber());
        assertEquals("Home renovation", result.getLoanReason());
        assertEquals(8.0, result.getEffectiveRate());
        assertEquals(8.0, result.getNominalRate());
        assertEquals(10000.0, result.getRemainingAmount());
        assertEquals(PaymentStatus.PENDING, result.getPaymentStatus());

        // The next payment date should be approximately 1 month from now
        LocalDate expectedDate = LocalDate.ofInstant(now, ZoneId.systemDefault()).plusMonths(1);
        assertEquals(expectedDate.getMonth(), result.getNextPaymentDate().getMonth());

        verify(loanRepository).save(any(Loan.class));
    }

    @Test
    public void testHasApprovedLoan_ReturnsTrue_WhenApprovedLoanExists() {

        Long userId = 1L;

        Account account = new Account();
        account.setId(1L);
        account.setOwnerID(userId);
        List<Account> userAccounts = Arrays.asList(account);

        when(accountRepository.findByOwnerID(userId)).thenReturn(userAccounts);

        Loan loan = new Loan();
        loan.setPaymentStatus(PaymentStatus.APPROVED);
        List<Loan> loans = Arrays.asList(loan);

        when(loanRepository.getLoansByAccount(account)).thenReturn(loans);

        boolean result = loanService.hasApprovedLoan(userId);


        assertTrue(result);  // Should return true if an approved loan exists
    }

    @Test
    public void testHasApprovedLoan_ReturnsFalse_WhenNoApprovedLoanExists() {

        Long userId = 1L;


        Account account = new Account();
        account.setId(1L);
        account.setOwnerID(userId);
        List<Account> userAccounts = Arrays.asList(account);

        when(accountRepository.findByOwnerID(userId)).thenReturn(userAccounts);


        Loan loan = new Loan();
        loan.setPaymentStatus(PaymentStatus.PAID_OFF);
        List<Loan> loans = Arrays.asList(loan);

        when(loanRepository.getLoansByAccount(account)).thenReturn(loans);


        boolean result = loanService.hasApprovedLoan(userId);

        assertFalse(result);
    }

    @Test
    public void testGetUserInstallments_ReturnsInstallments() {
        Long userId = 1L;


        when(accountRepository.findByOwnerID(userId)).thenReturn(Collections.singletonList(testAccount));


        when(loanRepository.getLoansByAccount(testAccount)).thenReturn(Collections.singletonList(savedLoan));

        when(loanRepository.save(any(Loan.class))).thenReturn(savedLoan);
        when(installmentsRepository.getByLoanId(savedLoan.getId())).thenReturn(Collections.singletonList(installment));

        // Mock save method of installmentsRepository
        when(installmentsRepository.save(installment)).thenReturn(installment);

        // Act
        List<Installment> result = loanService.getUserInstallments(userId);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(PaymentStatus.PENDING, result.get(0).getPaymentStatus());
        verify(accountRepository).findByOwnerID(userId);
        verify(loanRepository).getLoansByAccount(testAccount);
        verify(installmentsRepository).getByLoanId(savedLoan.getId());
    }

    @Test
    public void testCalculateRemainingInstallments_ReturnsRemainingInstallments() {
        Long ownerId = 1L;
        Long loanId = 1L;


        when(loanRepository.findById(loanId)).thenReturn(Optional.of(savedLoan));


        Integer remainingInstallments = loanService.calculateRemainingInstallments(ownerId, loanId);

        assertNotNull(remainingInstallments);
        assertEquals(9, remainingInstallments); // 12 - 6 paid installments
        verify(loanRepository).findById(loanId);
    }

    @Test
    public void testCalculateRemainingInstallments_ReturnsNull_WhenOwnerIdDoesNotMatch() {
        Long ownerId = 2L;
        Long loanId = 1L;


        when(loanRepository.findById(loanId)).thenReturn(Optional.of(savedLoan));


        Integer remainingInstallments = loanService.calculateRemainingInstallments(ownerId, loanId);

        assertNull(remainingInstallments);
        verify(loanRepository).findById(loanId);
    }

    @Test
    public void testCalculateRemainingInstallments_ThrowsException_WhenLoanNotFound() {
        Long ownerId = 1L;
        Long loanId = 99L;

        when(loanRepository.findById(loanId)).thenReturn(Optional.empty());
        assertThrows(RuntimeException.class, () -> loanService.calculateRemainingInstallments(ownerId, loanId));
        verify(loanRepository).findById(loanId);
    }

    @Test
    public void testGetPendingLoans() {


        savedLoan.setPaymentStatus(PaymentStatus.PENDING);
        when(loanRepository.save(savedLoan)).thenReturn(savedLoan);
        when(loanRepository.findByPaymentStatus(PaymentStatus.PENDING)).thenReturn(List.of(savedLoan));

        List<Loan> pendingLoans = loanService.getPendingLoans();

        assertNotNull(pendingLoans);
        assertEquals(1, pendingLoans.size());
        assertEquals(PaymentStatus.PENDING, pendingLoans.get(0).getPaymentStatus());
    }




}

