package com.settlement.reconciliation.dto;

import com.settlement.reconciliation.model.UnresolvedTransaction;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.ArrayList;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResultDto {
    
    private String sessionId;
    private int totalProcessed;
    private int settled;
    private int discrepant;
    private int missing;
    private int duplicate;
    private List<CrossSessionMatchDto> crossSessionMatches;
    private List<UnresolvedTransactionDto> unresolvedTransactions;
    
    // Cross-session integration statistics
    private int integratedFromPreviousSessions = 0;
    private int exactMatchesIntegrated = 0;
    private int fuzzyMatchesIntegrated = 0;
    private int manualMatchesIntegrated = 0;
    private double averageMatchConfidence = 0.0;
    private List<IntegratedTransactionDto> integratedTransactions = new ArrayList<>();
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnresolvedTransactionDto {
        private String id;
        private String stan;
        private String transactionRef;
        private Double amount;
        private String transactionDate;
        private String terminalId;
        private UnresolvedTransaction.TransactionStatus status;
        private String discrepancyType;
        private String details;
        private String originalSessionId;
        private String createdAt;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IntegratedTransactionDto {
        private String id;
        private String stan;
        private Double amount;
        private String transactionDate;
        private String terminalId;
        private String originalSessionId;
        private String matchId;
        private String matchType;
        private Double confidence;
        private String integrationType;
        private String integratedAt;
        private String status;
        private String notes;
    }
}
