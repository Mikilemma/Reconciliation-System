package com.settlement.reconciliation.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transaction_dedup_notifications", indexes = {
    @Index(name = "idx_tdn_session", columnList = "session_id"),
    @Index(name = "idx_tdn_file", columnList = "file_id"),
    @Index(name = "idx_tdn_existing_tx", columnList = "existing_transaction_id"),
    @Index(name = "idx_tdn_created_at", columnList = "created_at")
})
public class TransactionDedupNotification {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "session_id", columnDefinition = "VARCHAR(36)")
    private String sessionId;

    @Column(name = "file_id", columnDefinition = "VARCHAR(36)")
    private String fileId;

    @Column(name = "dedupe_key", length = 255, nullable = false)
    private String dedupeKey;

    @Column(name = "stan_no", length = 100)
    private String stanNo;

    @Column(name = "trans_ref", length = 100)
    private String transRef;

    @Column(name = "txn_amount", precision = 15, scale = 2)
    private BigDecimal txnAmount;

    @Column(name = "existing_transaction_id", columnDefinition = "VARCHAR(36)", nullable = false)
    private String existingTransactionId;

    @Column(name = "existing_status", length = 50)
    private String existingStatus;

    @Column(name = "reason", length = 50, nullable = false)
    private String reason;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
