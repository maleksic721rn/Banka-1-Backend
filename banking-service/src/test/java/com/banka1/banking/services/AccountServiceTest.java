package com.banka1.banking.services;

import com.banka1.banking.config.InterbankConfig;
import com.banka1.banking.dto.CreateCompanyDTO;
import com.banka1.banking.dto.CustomerDTO;
import com.banka1.banking.dto.NotificationDTO;
import com.banka1.banking.dto.TransactionResponseDTO;
import com.banka1.banking.dto.request.CreateAccountDTO;
import com.banka1.banking.models.Account;
import com.banka1.banking.models.Company;
import com.banka1.banking.models.Transaction;
import com.banka1.banking.models.helper.AccountSubtype;
import com.banka1.banking.models.helper.AccountType;
import com.banka1.banking.models.helper.CurrencyType;
import com.banka1.banking.repository.AccountRepository;
import com.banka1.banking.repository.TransactionRepository;
import com.banka1.common.listener.MessageHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.jms.core.JmsTemplate;

import javax.jms.TextMessage;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private BankAccountUtils bankAccountUtils;
    @Mock
    private ModelMapper modelMapper;
    @Mock
    private UserServiceCustomer userServiceCustomer;
    @InjectMocks
    private AccountService accountService;
    @Mock
    private CompanyService companyService;

    @Mock
    private JmsTemplate jmsTemplate;

    @Mock
    private MessageHelper messageHelper;
    @Mock
    private TextMessage mockTextMessage;
    @Mock
    private InterbankConfig interbankConfig;

    @Test
    public void testGetTransactionsForAccount_ReturnsMappedTransactions() {

        Long accountId = 1L;
        Account account = new Account();
        account.setId(accountId);
        account.setOwnerID(100L);

        Transaction txFrom = new Transaction();
        txFrom.setFromAccountId(account);
        txFrom.setToAccountId(account);

        Transaction txTo = new Transaction();
        Account otherAccount = new Account();
        otherAccount.setId(2L);
        txTo.setFromAccountId(otherAccount);
        txTo.setToAccountId(account);
        txTo.setBankOnly(false);

        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
        when(transactionRepository.findByFromAccountId(account)).thenReturn(List.of(txFrom));
        when(transactionRepository.findByToAccountId(account)).thenReturn(List.of(txTo));

        Account rsdAccount = new Account();
        rsdAccount.setOwnerID(100L);
        when(bankAccountUtils.getBankAccountForCurrency(CurrencyType.RSD)).thenReturn(rsdAccount);

        TransactionResponseDTO dto1 = new TransactionResponseDTO();
        dto1.setFromAccountId(account);
        dto1.setToAccountId(account);

        TransactionResponseDTO dto2 = new TransactionResponseDTO();
        dto2.setFromAccountId(otherAccount);
        dto2.setToAccountId(account);

        when(modelMapper.map(eq(txFrom), eq(TransactionResponseDTO.class))).thenReturn(dto1);
        when(modelMapper.map(eq(txTo), eq(TransactionResponseDTO.class))).thenReturn(dto2);

        CustomerDTO customer = new CustomerDTO();
        customer.setFirstName("John");
        customer.setLastName("Doe");

        when(userServiceCustomer.getCustomerById(any())).thenReturn(customer);


        List<TransactionResponseDTO> result = accountService.getTransactionsForAccount(accountId);


        assertEquals(2, result.size());
        assertEquals("John Doe", result.get(0).getSenderName());
        assertEquals("John Doe", result.get(0).getReceiverName());
        verify(accountRepository).findById(accountId);
        verify(transactionRepository).findByFromAccountId(account);
        verify(transactionRepository).findByToAccountId(account);
    }

    @Test
    public void testCreateAccount_success() {
        Long employeeId = 10L;
        Long ownerId = 20L;
        String companyNumber = "COMP123";

        CreateAccountDTO dto = new CreateAccountDTO();
        dto.setOwnerID(ownerId);
        dto.setCurrency(CurrencyType.RSD);
        dto.setType(AccountType.CURRENT);
        dto.setSubtype(AccountSubtype.BUSINESS);
        dto.setCreateCard(false);

        CreateCompanyDTO companyDTO = new CreateCompanyDTO();
        companyDTO.setCompanyNumber(companyNumber);
        dto.setCompanyData(companyDTO);

        CustomerDTO owner = new CustomerDTO();
        owner.setId(ownerId);
        owner.setEmail("test@email.com");
        owner.setFirstName("John");
        owner.setLastName("Doe");

        Account mappedAccount = new Account();
        mappedAccount.setSubtype(AccountSubtype.BUSINESS);
        mappedAccount.setCurrencyType(CurrencyType.RSD);
        mappedAccount.setBalance(100.0);

        Company company = new Company();
        company.setOwnerID(ownerId);

        Account savedAccount = new Account();
        savedAccount.setId(1L);
        savedAccount.setOwnerID(ownerId);

        when(userServiceCustomer.getCustomerById(ownerId)).thenReturn(owner);
        when(companyService.findByCompanyNumber(companyNumber)).thenReturn(company);
        when(modelMapper.map(dto, Account.class)).thenReturn(mappedAccount);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        dto.setType(AccountType.CURRENT);
        String notificationMessage = "Account created notification";
        when(messageHelper.createTextMessage(any(NotificationDTO.class))).thenReturn(notificationMessage);


        Account result = accountService.createAccount(dto, employeeId);

        assertEquals(savedAccount.getId(), result.getId());
        verify(accountRepository).save(any(Account.class));
    }


}
