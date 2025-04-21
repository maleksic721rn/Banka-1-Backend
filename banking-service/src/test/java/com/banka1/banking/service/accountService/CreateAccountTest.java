package com.banka1.banking.service.accountService;

import com.banka1.banking.dto.CreateCompanyDTO;
import com.banka1.banking.dto.CustomerDTO;
import com.banka1.banking.dto.request.CreateAccountDTO;
import com.banka1.banking.models.Account;
import com.banka1.banking.models.Card;
import com.banka1.banking.models.Company;
import com.banka1.banking.models.helper.*;
import com.banka1.banking.repository.AccountRepository;
import com.banka1.banking.services.AccountService;
import com.banka1.banking.services.CardService;
import com.banka1.banking.services.CompanyService;
import com.banka1.banking.services.UserServiceCustomer;
import com.banka1.common.listener.MessageHelper;
import com.banka1.common.model.BusinessActivityCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.modelmapper.ModelMapper;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CreateAccountTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private ModelMapper modelMapper;
    @InjectMocks
    private AccountService accountService;
    @Mock
    private UserServiceCustomer userServiceCustomer;
    @Mock
    private CardService cardService;
    @Mock
    JmsTemplate jmsTemplate;
    @Mock
    MessageHelper messageHelper;

    @Mock
    CompanyService companyService;

    /*
    {
      "ownerID": 0,
      "currency": "RSD",
      "type": "CURRENT",
      "subtype": "PERSONAL",
      "dailyLimit": 0,
      "monthlyLimit": 0,
      "status": "ACTIVE"
    }
*/

    private CreateAccountDTO createAccountDTO;
    private Account acc;
    private CustomerDTO customerDTO;

    @BeforeEach
    void setup() {
        createAccountDTO = new CreateAccountDTO();
        createAccountDTO.setOwnerID(1L);
        createAccountDTO.setCurrency(CurrencyType.RSD);
        createAccountDTO.setType(AccountType.CURRENT);
        createAccountDTO.setSubtype(AccountSubtype.PERSONAL);
        createAccountDTO.setDailyLimit(0.0);
        createAccountDTO.setMonthlyLimit(0.0);
        createAccountDTO.setStatus(AccountStatus.ACTIVE);
        createAccountDTO.setCreateCard(true);

        acc = new Account();
        acc.setType(AccountType.CURRENT);
        acc.setOwnerID(1L);
        acc.setCurrencyType(CurrencyType.RSD);
        acc.setType(AccountType.CURRENT);
        acc.setSubtype(AccountSubtype.PERSONAL);
        acc.setDailyLimit(0.0);
        acc.setMonthlyLimit(0.0);
        acc.setStatus(AccountStatus.ACTIVE);
        acc.setAccountNumber("111000112345678911");

        customerDTO = new CustomerDTO();
        customerDTO.setId(1L);

    }
    @Test
    public void createAccountSuccesfullyTest() {

        when(modelMapper.map(createAccountDTO, Account.class)).thenReturn(acc);

        when(accountRepository.save(acc)).thenReturn(acc);

        when(userServiceCustomer.getCustomerById(anyLong())).thenReturn(customerDTO);

        when(cardService.createCard(any())).thenReturn(new Card());

        when(messageHelper.createTextMessage(any())).thenReturn("a");

        doNothing().when(jmsTemplate).convertAndSend((String) any(), anyString());

        Account result = accountService.createAccount(createAccountDTO, 1L);

        assertNotNull(result);
        assertEquals(acc.getAccountNumber().substring(0,7), result.getAccountNumber().substring(0,7));
        assertEquals(acc.getAccountNumber().substring(16,18), result.getAccountNumber().substring(16,18));
        verify(accountRepository, times(1)).save(acc);
    }

    @Test
    public void createAccountBadCombinationTest() {
        createAccountDTO.setCurrency(CurrencyType.EUR);

        when(userServiceCustomer.getCustomerById(anyLong())).thenReturn(customerDTO);
        
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            accountService.createAccount(createAccountDTO, 1L);
        });
        assertEquals("400 BAD_REQUEST \"Nevalidna kombinacija vrste racuna i valute\"", exception.getMessage());

        verify(accountRepository, never()).save(any());
    }

    @Test
    public void createAccountWithNewCompanyTest() {
        createAccountDTO.setSubtype(AccountSubtype.BUSINESS);
        acc.setSubtype(AccountSubtype.BUSINESS);

        CreateCompanyDTO companyDTO = new CreateCompanyDTO();
        String companyNumber = "12345678";
        companyDTO.setCompanyNumber(companyNumber);
        companyDTO.setVatNumber("123456789");
        companyDTO.setAddress("Bulevar Banke 1");
        companyDTO.setName("Test Company");
        companyDTO.setBas(BusinessActivityCode.COMPUTER_PROGRAMMING);

        createAccountDTO.setCompanyData(companyDTO);
        createAccountDTO.setCreateCard(false);

        when(userServiceCustomer.getCustomerById(createAccountDTO.getOwnerID())).thenReturn(customerDTO);
        when(modelMapper.map(createAccountDTO, Account.class)).thenReturn(acc);

        when(companyService.findByCompanyNumber(companyNumber)).thenReturn(null);

        Company newCompany = new Company();
        newCompany.setId(10L);
        newCompany.setCompanyNumber(companyNumber);
        newCompany.setName(companyDTO.getName());

        when(companyService.createCompany(any(CreateCompanyDTO.class))).thenReturn(newCompany);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        when(accountRepository.save(accountCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Account result = accountService.createAccount(createAccountDTO, 5L);

        assertNotNull(result);
        assertNotNull(result.getCompany());
        assertEquals(companyNumber, result.getCompany().getCompanyNumber());
        assertEquals(customerDTO.getId(), result.getCompany().getOwnerID());

        verify(userServiceCustomer, times(1)).getCustomerById(createAccountDTO.getOwnerID());
        verify(modelMapper, times(1)).map(createAccountDTO, Account.class);
        verify(companyService, times(1)).findByCompanyNumber(companyNumber); // Verify check was done
        verify(companyService, times(1)).createCompany(any(CreateCompanyDTO.class)); // Verify creation was called
        verify(accountRepository, times(1)).save(any(Account.class)); // Verify account was saved
        verify(cardService, never()).createCard(any()); // Card creation was false

        Account savedAccount = accountCaptor.getValue();
        assertNotNull(savedAccount.getCompany());
        assertEquals(companyNumber, savedAccount.getCompany().getCompanyNumber());
        assertEquals(customerDTO.getId(), savedAccount.getCompany().getOwnerID()); // Verify ownerID on saved account's company reference
        assertEquals(AccountSubtype.BUSINESS, savedAccount.getSubtype()); // Verify subtype

    }

}
