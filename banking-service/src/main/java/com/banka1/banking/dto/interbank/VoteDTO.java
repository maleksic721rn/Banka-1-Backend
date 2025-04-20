package com.banka1.banking.dto.interbank;

import lombok.Data;
import lombok.Setter;

import java.util.List;

@Data
@Setter
public class VoteDTO {
    private String vote; // "YES" ili "NO"
    private List<VoteReasonDTO> reasons; // može biti null ako je "YES"
}
