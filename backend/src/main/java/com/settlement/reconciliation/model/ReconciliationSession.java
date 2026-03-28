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
@Table(name = "reconciliation_sessions")
public class ReconciliationSession {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "settlement_date", nullable = false, columnDefinition = "TEXT")
    private String settlementDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "total_transactions", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int totalTransactions;

    @Column(name = "settled_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int settledCount;

    @Column(name = "discrepant_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int discrepantCount;

    @Column(name = "missing_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int missingCount;

    @Column(name = "duplicate_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int duplicateCount;

    @Column(name = "net_settlement_amount", nullable = false, precision = 18, scale = 2)
    @org.hibernate.annotations.ColumnDefault("0")
    private BigDecimal netSettlementAmount;

    @Column(name = "match_rate", nullable = false, precision = 5, scale = 2)
    @org.hibernate.annotations.ColumnDefault("0")
    private BigDecimal matchRate;

    @Column(name = "processed_at", nullable = true)
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        processedAt = LocalDateTime.now();
    }
}