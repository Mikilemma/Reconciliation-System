package com.settlement.reconciliation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transactions_atm", indexes = {
    @Index(name = "idx_stan_no", columnList = "stanNo"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_file_id", columnList = "fileId"),
    @Index(name = "idx_session_id", columnList = "sessionId")
})
public class TransactionAtm {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "trans_ref", length = 50)
    private String transRef;

    @Column(name = "company_code", length = 20)
    private String companyCode;

    @Column(name = "proc_code", length = 50)
    private String procCode;

    @Column(name = "mti_code", length = 255)
    private String mtiCode;

    @Column(name = "pan_number", length = 30)
    private String panNumber;

    @Column(name = "retrieval_ref_no", length = 50)
    private String retrievalRefNo;

    @Column(name = "auth_code", length = 20)
    private String authCode;

    @Column(name = "stan_no", length = 100)
    private String stanNo;

    @Column(name = "txn_amount", precision = 15, scale = 2)
    private BigDecimal txnAmount;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "booking_date")
    private LocalDateTime bookingDate;

    @Column(name = "debit_acct_no", length = 50)
    private String debitAcctNo;

    @Column(name = "credit_acct_no", length = 50)
    private String creditAcctNo;

    @Column(name = "request_time", length = 50)
    private String requestTime;

    @Column(name = "card_acc_id", length = 30)
    private String cardAccId;

    @Column(name = "status", length = 50)
    @org.hibernate.annotations.ColumnDefault("'unsettled'")
    private String status;

    @Column(name = "file_id", columnDefinition = "VARCHAR(36)")
    private String fileId;

    @Column(name = "session_id", columnDefinition = "VARCHAR(36)")
    private String sessionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}