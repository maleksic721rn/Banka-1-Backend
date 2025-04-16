package com.banka1.banking.repository;

import com.banka1.banking.models.Currency;
import com.banka1.banking.models.Event;
import com.banka1.banking.models.EventDelivery;
import com.banka1.banking.models.helper.CurrencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventDeliveryRepository extends JpaRepository<EventDelivery, Long> {
    List<EventDelivery> findByEvent(Event event);
}
