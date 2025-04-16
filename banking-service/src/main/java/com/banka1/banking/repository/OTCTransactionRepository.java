package com.banka1.banking.repository;

import com.banka1.banking.models.OTCTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OTCTransactionRepository extends JpaRepository<OTCTransaction, Long> {
    Optional<OTCTransaction> findByUid(String uid);
}
