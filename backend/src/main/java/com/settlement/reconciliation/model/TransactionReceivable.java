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
@Table(name = "transactions_receivable", indexes = {
    @Index(name = "idx_reference", columnList = "reference"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_file_id", columnList = "fileId"),
    @Index(name = "idx_session_id", columnList = "sessionId"),
    @Index(name = "idx_book_date", columnList = "bookDate")
})
public class TransactionReceivable {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "book_date")
    private LocalDateTime bookDate;

    @Column(name = "reference", length = 100)
    private String reference;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "value_date")
    private LocalDateTime valueDate;

    @Column(name = "debit", precision = 15, scale = 2)
    private BigDecimal debit;

    @Column(name = "credit", precision = 15, scale = 2)
    private BigDecimal credit;

    @Column(name = "closing_balance", precision = 15, scale = 2)
    private BigDecimal closingBalance;

    @Column(name = "account_number", length = 50)
    private String accountNumber;

    @Column(name = "customer_name", length = 200)
    private String customerName;

    @Column(name = "currency", length = 10)
    private String currency;

    @Column(name = "account_type", length = 100)
    private String accountType;

    @Column(name = "branch", length = 100)
    private String branch;

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
