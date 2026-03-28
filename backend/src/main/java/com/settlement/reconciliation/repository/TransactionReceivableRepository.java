package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.TransactionReceivable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionReceivableRepository extends JpaRepository<TransactionReceivable, String> {
    
    List<TransactionReceivable> findBySessionId(String sessionId);
    
    List<TransactionReceivable> findByFileId(String fileId);
    
    List<TransactionReceivable> findByStatus(String status);
    
    Optional<TransactionReceivable> findByReference(String reference);
    
    List<TransactionReceivable> findByBookDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    List<TransactionReceivable> findByAccountNumber(String accountNumber);
    
    @Query("SELECT t FROM TransactionReceivable t WHERE t.sessionId = :sessionId AND t.status = :status")
    List<TransactionReceivable> findBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") String status);
    
    @Query("SELECT COUNT(t) FROM TransactionReceivable t WHERE t.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);
    
    @Query("SELECT COUNT(t) FROM TransactionReceivable t WHERE t.sessionId = :sessionId AND t.status = :status")
    long countBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") String status);
    
    @Query("SELECT SUM(t.debit) FROM TransactionReceivable t WHERE t.sessionId = :sessionId AND t.status = 'settled'")
    java.math.BigDecimal sumDebitBySessionIdAndStatus(@Param("sessionId") String sessionId);
}