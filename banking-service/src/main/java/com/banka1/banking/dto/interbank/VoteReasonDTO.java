package com.banka1.banking.dto.interbank;

import com.banka1.banking.dto.interbank.newtx.PostingDTO;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VoteReasonDTO {
    private String reason; // npr. "NO_SUCH_ACCOUNT", "UNSUPPORTED_ASSET", itd.
    private PostingDTO posting; // isti format kao u zahtevu

    public VoteReasonDTO() {
    }
}