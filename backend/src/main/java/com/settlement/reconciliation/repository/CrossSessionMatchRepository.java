package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.CrossSessionMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CrossSessionMatchRepository extends JpaRepository<CrossSessionMatch, String> {
    
    // Find all matches for a specific transaction
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE csm.originalTransactionId = :transactionId OR csm.matchedTransactionId = :transactionId")
    List<CrossSessionMatch> findByTransactionId(@Param("transactionId") String transactionId);
    
    // Find matches for a specific session
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE csm.originalSessionId = :sessionId OR csm.matchedSessionId = :sessionId")
    List<CrossSessionMatch> findBySessionId(@Param("sessionId") String sessionId);
    
    // Find matches by type
    List<CrossSessionMatch> findByMatchType(CrossSessionMatch.MatchType matchType);
    
    // Find matches by confidence range
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE csm.confidence >= :minConfidence AND csm.confidence <= :maxConfidence")
    List<CrossSessionMatch> findByConfidenceRange(@Param("minConfidence") Double minConfidence, 
                                                 @Param("maxConfidence") Double maxConfidence);
    
    // Find matches by review status
    List<CrossSessionMatch> findByReviewStatus(CrossSessionMatch.ReviewStatus reviewStatus);
    
    // Find recent matches (last N days)
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE csm.matchedAt >= :since")
    List<CrossSessionMatch> findRecentMatches(@Param("since") LocalDateTime since);
    
    // Find matches that need review (fuzzy matches below confidence threshold)
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE csm.matchType = 'FUZZY' AND csm.confidence < :threshold AND csm.reviewStatus = :status")
    List<CrossSessionMatch> findMatchesNeedingReview(@Param("threshold") Double threshold, @Param("status") CrossSessionMatch.ReviewStatus status);
    
    // Count matches by type
    @Query("SELECT csm.matchType, COUNT(csm) FROM CrossSessionMatch csm GROUP BY csm.matchType")
    List<Object[]> countMatchesByType();
    
    // Count matches by confidence ranges
    @Query("SELECT " +
           "CASE " +
           "  WHEN csm.confidence >= 95 THEN 'HIGH' " +
           "  WHEN csm.confidence >= 80 THEN 'MEDIUM' " +
           "  ELSE 'LOW' " +
           "END as confidenceLevel, " +
           "COUNT(csm) " +
           "FROM CrossSessionMatch csm " +
           "GROUP BY " +
           "CASE " +
           "  WHEN csm.confidence >= 95 THEN 'HIGH' " +
           "  WHEN csm.confidence >= 80 THEN 'MEDIUM' " +
           "  ELSE 'LOW' " +
           "END")
    List<Object[]> countMatchesByConfidenceLevel();
    
    // Find matches between two specific sessions
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE " +
           "(csm.originalSessionId = :session1Id AND csm.matchedSessionId = :session2Id) OR " +
           "(csm.originalSessionId = :session2Id AND csm.matchedSessionId = :session1Id)")
    List<CrossSessionMatch> findMatchesBetweenSessions(@Param("session1Id") String session1Id, 
                                                       @Param("session2Id") String session2Id);
    
    // Check if a match already exists between two transactions
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE " +
           "(csm.originalTransactionId = :tx1Id AND csm.matchedTransactionId = :tx2Id) OR " +
           "(csm.originalTransactionId = :tx2Id AND csm.matchedTransactionId = :tx1Id)")
    Optional<CrossSessionMatch> findMatchBetweenTransactions(@Param("tx1Id") String tx1Id, 
                                                             @Param("tx2Id") String tx2Id);
    
    // Find matches resolved by system vs user
    @Query("SELECT csm.resolvedBy, COUNT(csm) FROM CrossSessionMatch csm GROUP BY csm.resolvedBy")
    List<Object[]> countMatchesByResolvedBy();
    
    // Find high confidence matches that were rejected
    @Query("SELECT csm FROM CrossSessionMatch csm WHERE csm.confidence >= :threshold AND csm.reviewStatus = :status")
    List<CrossSessionMatch> findHighConfidenceRejectedMatches(@Param("threshold") Double threshold, @Param("status") CrossSessionMatch.ReviewStatus status);
}
