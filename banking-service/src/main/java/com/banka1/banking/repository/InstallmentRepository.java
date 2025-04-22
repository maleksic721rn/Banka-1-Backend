package com.banka1.banking.repository;

import com.banka1.banking.models.Installment;
import com.banka1.banking.models.Loan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InstallmentRepository extends JpaRepository<Installment, Long> {
    Integer countByLoan(Loan loan);
}
