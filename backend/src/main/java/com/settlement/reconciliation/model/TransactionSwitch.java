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
@Table(name = "transactions_switch", indexes = {
    @Index(name = "idx_stan", columnList = "stan"),
    @Index(name = "idx_ref_num", columnList = "refNum"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_file_id", columnList = "fileId"),
    @Index(name = "idx_session_id", columnList = "sessionId"),
    @Index(name = "idx_transaction_date", columnList = "transactionDate")
})
public class TransactionSwitch {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "stan", length = 100)
    private String stan;

    @Column(name = "ref_num", length = 50)
    private String refNum;

    @Column(name = "issuer", length = 100)
    private String issuer;

    @Column(name = "acquirer", length = 100)
    private String acquirer;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "terminal_id", length = 50)
    private String terminalId;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "transaction_description", columnDefinition = "TEXT")
    private String transactionDescription;

    @Column(name = "debit_account", length = 50)
    private String debitAccount;

    @Column(name = "credit_account", length = 50)
    private String creditAccount;

    @Column(name = "book_date")
    private LocalDateTime bookDate;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "closing_balance", precision = 15, scale = 2)
    private BigDecimal closingBalance;

    @Column(columnDefinition = "VARCHAR(50)")
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
