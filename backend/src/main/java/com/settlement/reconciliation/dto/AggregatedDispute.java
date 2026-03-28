package com.settlement.reconciliation.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * DTO for aggregated disputes - consolidates multiple dispute records
 * for the same transaction (by transactionRef) into a single UI entry.
 * This is for display purposes only and does not affect underlying data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AggregatedDispute {
    
    private String id; // Uses first dispute's ID as primary
    private String sessionId;
    private String transactionRef;
    private String stan;
    private BigDecimal amount;
    private String transactionDate;
    private String terminalId;
    
    private String disputeStatus; // Resolved if ANY are resolved, else most severe
    private String disputeReason;
    private String originalStatus;
    private String discrepancyType;
    private String details;
    
    private String resolutionNotes;
    private String resolvedBy;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
    
    // Aggregated source data from all records
    private String switchData;
    private String atmData;
    private String payableData;
    private String receivableData;
    
    // List of source files this transaction appears in
    private List<String> sourceFiles = new ArrayList<>();
    
    // IDs of all underlying dispute records (for resolution updates)
    private List<String> underlyingDisputeIds = new ArrayList<>();
    
    // Count of records aggregated
    private int recordCount;
}
