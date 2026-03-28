package com.settlement.reconciliation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "reconciliation_results", indexes = {
    @Index(name = "idx_result_session_id", columnList = "session_id"),
    @Index(name = "idx_result_status", columnList = "status"),
    @Index(name = "idx_result_stan", columnList = "stan"),
    @Index(name = "idx_result_trans_ref", columnList = "transaction_ref"),
    @Index(name = "idx_result_session_status", columnList = "session_id,status"),
    @Index(name = "idx_result_session_trans_ref_status", columnList = "session_id,transaction_ref,status"),
    @Index(name = "idx_result_session_stan_status", columnList = "session_id,stan,status")
})
public class ReconciliationResult {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "session_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private String sessionId;

    @Column(columnDefinition = "VARCHAR(100)")
    private String stan;

    @Column(name = "transaction_ref", columnDefinition = "VARCHAR(100)")
    private String transactionRef;

    @Column(precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", columnDefinition = "VARCHAR(100)")
    private String transactionDate;

    @Column(name = "terminal_id", columnDefinition = "VARCHAR(50)")
    private String terminalId;

    @Column(nullable = false, columnDefinition = "VARCHAR(50)")
    private String status;

    @Column(name = "discrepancy_type", columnDefinition = "TEXT")
    private String discrepancyType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "source_files", nullable = false, columnDefinition = "TEXT")
    private String sourceFiles;

    @Column(name = "switch_data", columnDefinition = "TEXT")
    private String switchData;

    @Column(name = "atm_data", columnDefinition = "TEXT")
    private String atmData;

    @Column(name = "payable_data", columnDefinition = "TEXT")
    private String payableData;

    @Column(name = "receivable_data", columnDefinition = "TEXT")
    private String receivableData;
}
