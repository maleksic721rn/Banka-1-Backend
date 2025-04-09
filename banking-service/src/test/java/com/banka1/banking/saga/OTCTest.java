package com.banka1.banking.saga;

import com.banka1.banking.services.OTCService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OTCTest {
    @InjectMocks
    private OTCService otcService;

    @Test
    void otcTest_successfulTransaction() {

    }

    @Test
    void otcTest_rollback() {

    }
}
