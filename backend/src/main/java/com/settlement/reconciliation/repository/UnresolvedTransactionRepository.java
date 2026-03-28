package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.UnresolvedTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UnresolvedTransactionRepository extends JpaRepository<UnresolvedTransaction, String> {
    
    // Find unresolved transactions that haven't been resolved yet
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findUnresolvedTransactions();
    
    // Find by status and unresolved
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.status = :status AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findUnresolvedByStatus(@Param("status") UnresolvedTransaction.TransactionStatus status);
    
    // Find potential matches by STAN and amount (exact match)
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.stan = :stan AND ut.amount = :amount AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findPotentialExactMatches(@Param("stan") String stan, @Param("amount") BigDecimal amount);
    
    // Find potential matches by amount and terminal ID (fuzzy match)
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.amount = :amount AND ut.terminalId = :terminalId AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findPotentialFuzzyMatches(@Param("amount") BigDecimal amount, @Param("terminalId") String terminalId);
    
    // Find potential matches by amount within time range (date range fuzzy match)
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.amount = :amount AND ut.transactionDate BETWEEN :startDate AND :endDate AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findPotentialDateRangeMatches(
        @Param("amount") BigDecimal amount, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );
    
    // Find by original session
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.originalSessionId = :sessionId")
    List<UnresolvedTransaction> findByOriginalSession(@Param("sessionId") String sessionId);
    
    // Count unresolved by status
    @Query("SELECT COUNT(ut) FROM UnresolvedTransaction ut WHERE ut.status = :status AND ut.resolvedAt IS NULL")
    long countUnresolvedByStatus(@Param("status") UnresolvedTransaction.TransactionStatus status);
    
    // Mark transactions as resolved
    @Modifying
    @Query("UPDATE UnresolvedTransaction ut SET ut.resolvedAt = :resolvedAt, ut.resolvedBySessionId = :resolvedBySessionId WHERE ut.id IN :transactionIds")
    void markAsResolved(@Param("transactionIds") List<String> transactionIds, 
                       @Param("resolvedAt") LocalDateTime resolvedAt, 
                       @Param("resolvedBySessionId") String resolvedBySessionId);
    
    // Find transactions created after a specific date
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.createdAt >= :date AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findUnresolvedAfterDate(@Param("date") LocalDateTime date);
    
    // Find by STAN (exact match)
    Optional<UnresolvedTransaction> findByStanAndResolvedAtIsNull(String stan);
    
    // Find by transaction reference
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.transactionRef = :transactionRef AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findByTransactionRefAndUnresolved(@Param("transactionRef") String transactionRef);

    // Batch match by STAN + Amount
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.stan IN :stans AND ut.amount IN :amounts AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findByStanInAndAmountInUnresolved(@Param("stans") List<String> stans,
                                                                  @Param("amounts") List<BigDecimal> amounts);

    // Batch match by Transaction Ref + Amount
    @Query("SELECT ut FROM UnresolvedTransaction ut WHERE ut.transactionRef IN :refs AND ut.amount IN :amounts AND ut.resolvedAt IS NULL")
    List<UnresolvedTransaction> findByTransactionRefInAndAmountInUnresolved(@Param("refs") List<String> refs,
                                                                            @Param("amounts") List<BigDecimal> amounts);
}
