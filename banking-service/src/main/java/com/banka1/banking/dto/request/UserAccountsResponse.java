package com.banka1.banking.dto.request;

import com.banka1.banking.models.Account;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserAccountsResponse {
    private List<Account> accounts;
}
