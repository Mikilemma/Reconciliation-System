package com.settlement.reconciliation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "cross_session_matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrossSessionMatch {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "original_transaction_id", nullable = false)
    private String originalTransactionId;
    
    @Column(name = "original_session_id", nullable = false)
    private String originalSessionId;
    
    @Column(name = "matched_transaction_id", nullable = false)
    private String matchedTransactionId;
    
    @Column(name = "matched_session_id", nullable = false)
    private String matchedSessionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false)
    private MatchType matchType;
    
    @Column(name = "confidence", nullable = false)
    private Double confidence; // 0.0 to 100.0
    
    @Column(name = "matched_at", nullable = false)
    private LocalDateTime matchedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "resolved_by", nullable = false)
    private ResolvedBy resolvedBy;
    
    @Column(name = "match_notes", columnDefinition = "TEXT")
    private String matchNotes;
    
    @Column(name = "review_status")
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    
    @Column(name = "reviewed_by")
    private String reviewedBy;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;
    
    @PrePersist
    protected void onCreate() {
        matchedAt = LocalDateTime.now();
    }
    
    public enum MatchType {
        EXACT,
        FUZZY,
        MANUAL
    }
    
    public enum ResolvedBy {
        SYSTEM,
        USER
    }
    
    public enum ReviewStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
