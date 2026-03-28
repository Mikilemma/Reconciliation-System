package com.settlement.reconciliation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "disputes", indexes = {
    @Index(name = "idx_dispute_status", columnList = "dispute_status"),
    @Index(name = "idx_dispute_transaction_id", columnList = "transaction_id"),
    @Index(name = "idx_dispute_stan", columnList = "stan"),
    @Index(name = "idx_dispute_created_at", columnList = "created_at")
})
public class Dispute {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "transaction_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String transactionId;

    @Column(columnDefinition = "TEXT")
    private String stan;

    @Column(name = "transaction_ref", columnDefinition = "TEXT")
    private String transactionRef;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", columnDefinition = "TEXT")
    private String transactionDate;

    @Column(name = "terminal_id", columnDefinition = "TEXT")
    private String terminalId;

    @Column(name = "dispute_status", nullable = false, columnDefinition = "TEXT")
    @org.hibernate.annotations.ColumnDefault("'open'")
    private String disputeStatus;

    @Column(name = "dispute_reason", nullable = false, columnDefinition = "TEXT")
    private String disputeReason;

    @Column(name = "original_status", nullable = false, columnDefinition = "TEXT")
    private String originalStatus;

    @Column(name = "discrepancy_type", columnDefinition = "TEXT")
    private String discrepancyType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "switch_data", columnDefinition = "TEXT")
    private String switchData;

    @Column(name = "atm_data", columnDefinition = "TEXT")
    private String atmData;

    @Column(name = "payable_data", columnDefinition = "TEXT")
    private String payableData;

    @Column(name = "receivable_data", columnDefinition = "TEXT")
    private String receivableData;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at", columnDefinition = "DATETIME")
    private LocalDateTime resolvedAt;

    @Column(name = "resolved_by", columnDefinition = "TEXT")
    private String resolvedBy;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

    @Column(name = "matched_transaction_id", columnDefinition = "VARCHAR(36)")
    private String matchedTransactionId;
}
