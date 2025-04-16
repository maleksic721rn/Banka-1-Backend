package com.banka1.banking.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "otc_transaction")
public class OTCTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "seller_account_id", nullable = false)
    @ManyToOne
    private Account sellerAccount;

    @JoinColumn(name = "buyer_account_id", nullable = false)
    @ManyToOne
    private Account buyerAccount;

    @Column(nullable = false)
    private Double amount;

    @Column(unique = true)
    private String uid;

    @Column(nullable = false)
    private Boolean failed = false;

    @Column(nullable = false)
    private Boolean finished = false;

    @Column(nullable = false)
    private Double amountTaken = -1.0;

    @Column(nullable = false)
    private Double amountGiven = -1.0;
}
