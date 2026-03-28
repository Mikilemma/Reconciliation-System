package com.settlement.reconciliation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.util.List;
import java.util.ArrayList;

/**
 * DTO for aggregated reconciliation results - consolidates multiple result records
 * for the same transaction into a single UI entry.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedReconciliationResult {
    
    private String id;
    private String sessionId;
    private String stan;
    private String transactionRef;
    private BigDecimal amount;
    private String transactionDate;
    private String terminalId;
    private String status;
    private String discrepancyType;
    private String details;
    
    // Aggregated source data
    private String switchData;
    private String atmData;
    private String payableData;
    private String receivableData;
    
    // List of source files
    private String sourceFiles;
    
    // Count of records aggregated
    private int recordCount;
}
