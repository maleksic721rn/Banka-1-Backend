package com.banka1.banking.dto.interbank.newtx.assets;

import com.banka1.banking.dto.interbank.newtx.AssetDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OptionAssetDTO extends AssetDTO {
    private OptionDescription asset;
}

