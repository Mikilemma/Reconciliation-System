package com.settlement.reconciliation.dto;

import com.settlement.reconciliation.model.CrossSessionMatch;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrossSessionMatchDto {
    
    private String id;
    private String originalTransactionId;
    private String originalSessionId;
    private String matchedTransactionId;
    private String matchedSessionId;
    private CrossSessionMatch.MatchType matchType;
    private Double confidence;
    private LocalDateTime matchedAt;
    private CrossSessionMatch.ResolvedBy resolvedBy;
    private String matchNotes;
    private CrossSessionMatch.ReviewStatus reviewStatus;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    
    // Additional fields for API responses
    private TransactionSummary originalTransaction;
    private TransactionSummary matchedTransaction;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummary {
        private String id;
        private String stan;
        private String transactionRef;
        private Double amount;
        private LocalDateTime transactionDate;
        private String terminalId;
        private String status;
        private String discrepancyType;
    }
}
