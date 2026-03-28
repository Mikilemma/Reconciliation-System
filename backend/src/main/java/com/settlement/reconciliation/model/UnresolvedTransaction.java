package com.settlement.reconciliation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "unresolved_transactions", indexes = {
    @Index(name = "idx_ut_stan_amount_resolved", columnList = "stan,amount,resolved_at"),
    @Index(name = "idx_ut_ref_amount_resolved", columnList = "transaction_ref,amount,resolved_at"),
    @Index(name = "idx_ut_amount_resolved", columnList = "amount,resolved_at"),
    @Index(name = "idx_ut_original_session", columnList = "original_session_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnresolvedTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "stan", length = 50)
    private String stan;
    
    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;
    
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;
    
    @Column(name = "terminal_id", length = 50)
    private String terminalId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;
    
    @Column(name = "discrepancy_type", length = 100)
    private String discrepancyType;
    
    @Column(name = "details", columnDefinition = "TEXT")
    private String details;
    
    @Column(name = "original_session_id", nullable = false)
    private String originalSessionId;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by_session_id")
    private String resolvedBySessionId;
    
    @Column(name = "source_files", length = 500)
    private String sourceFiles;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum TransactionStatus {
        DISCREPANT,
        MISSING,
        DUPLICATE
    }
}
