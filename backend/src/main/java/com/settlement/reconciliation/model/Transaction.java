
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
@Table(name = "transactions", indexes = {
    @Index(name = "idx_stan_no", columnList = "stan_no"),
    @Index(name = "idx_trans_ref", columnList = "trans_ref"),
    @Index(name = "idx_session_id", columnList = "session_id"),
    @Index(name = "idx_file_id", columnList = "file_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_value_date", columnList = "value_date"),
    @Index(name = "idx_source", columnList = "source"),
    @Index(name = "idx_issuer", columnList = "issuer"),
    @Index(name = "idx_acquirer", columnList = "acquirer"),
    @Index(name = "idx_session_source", columnList = "session_id,source"),
    @Index(name = "idx_session_source_stan_amt", columnList = "session_id,source,stan_no,txn_amount"),
    @Index(name = "idx_session_source_ref_amt", columnList = "session_id,source,trans_ref,txn_amount"),
    @Index(name = "idx_session_source_ref", columnList = "session_id,source,trans_ref"),
    @Index(name = "idx_dedupe_key", columnList = "dedupe_key")
})
public class Transaction {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "auth_code", length = 20)
    private String authCode;

    @Column(name = "booking_date")
    private LocalDateTime bookingDate;

    @Column(name = "card_acc_id", length = 30)
    private String cardAccId;

    @Column(name = "company_code", length = 20)
    private String companyCode;

    @Column(name = "credit_acct_no", length = 50)
    private String creditAcctNo;

    @Column(name = "debit_acct_no", length = 50)
    private String debitAcctNo;

    @Column(name = "issuer", length = 100)
    private String issuer;

    @Column(name = "acquirer", length = 100)
    private String acquirer;

    @Column(name = "transaction_description", length = 200)
    private String transactionDescription;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "file_id", columnDefinition = "VARCHAR(36)")
    private String fileId;

    @Column(name = "mti_code", length = 10)
    private String mtiCode;

    @Column(name = "pan_number", length = 30)
    private String panNumber;

    @Column(name = "proc_code", length = 10)
    private String procCode;

    @Column(name = "request_time", length = 50)
    private String requestTime;

    @Column(name = "retrieval_ref_no", length = 50)
    private String retrievalRefNo;

    @Column(name = "session_id", columnDefinition = "VARCHAR(36)")
    private String sessionId;

    @Column(name = "stan_no", length = 100)
    private String stanNo;

    @Column(columnDefinition = "VARCHAR(50)")
    private String status;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    @Column(name = "trans_ref", length = 50)
    private String transRef;

    @Column(name = "txn_amount", precision = 15, scale = 2)
    private BigDecimal txnAmount;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "source", length = 20) // To distinguish between Switch, ATM, etc.
    private String source;

    @Column(name = "dedupe_key", length = 255, unique = true)
    private String dedupeKey;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "credit", precision = 15, scale = 2)
    private BigDecimal credit;

    @Column(name = "debit", precision = 15, scale = 2)
    private BigDecimal debit;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
