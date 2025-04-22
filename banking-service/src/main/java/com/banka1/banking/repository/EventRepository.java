package com.banka1.banking.repository;

import com.banka1.banking.models.Currency;
import com.banka1.banking.models.Event;
import com.banka1.banking.models.helper.CurrencyType;
import com.banka1.banking.models.helper.IdempotenceKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<Event, Long> {
    boolean existsByIdempotenceKey(IdempotenceKey idempotenceKey);
    Optional<Event> findByIdempotenceKey(IdempotenceKey idempotenceKey);
}
