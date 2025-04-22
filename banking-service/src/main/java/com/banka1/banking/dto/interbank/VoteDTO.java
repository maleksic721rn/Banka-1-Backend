package com.banka1.banking.dto.interbank;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Setter;

import java.util.List;

@Data
@Setter
public class VoteDTO {
    private String vote; // "YES" ili "NO"

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<VoteReasonDTO> reasons; // može biti null ako je "YES"
}
