package com.banka1.banking.services;

import com.banka1.banking.models.Event;

public interface InterbankOperationService {
    void sendCommit(Event event);
    void sendRollback(Event event);
}
