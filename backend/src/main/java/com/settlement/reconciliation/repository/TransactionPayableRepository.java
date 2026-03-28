package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.TransactionPayable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionPayableRepository extends JpaRepository<TransactionPayable, String> {
    
    List<TransactionPayable> findBySessionId(String sessionId);
    
    List<TransactionPayable> findByFileId(String fileId);
    
    List<TransactionPayable> findByStatus(String status);
    
    Optional<TransactionPayable> findByReference(String reference);
    
    List<TransactionPayable> findByBookDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    List<TransactionPayable> findByAccountNumber(String accountNumber);
    
    @Query("SELECT t FROM TransactionPayable t WHERE t.sessionId = :sessionId AND t.status = :status")
    List<TransactionPayable> findBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") String status);
    
    @Query("SELECT COUNT(t) FROM TransactionPayable t WHERE t.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);
    
    @Query("SELECT COUNT(t) FROM TransactionPayable t WHERE t.sessionId = :sessionId AND t.status = :status")
    long countBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") String status);
    
    @Query("SELECT SUM(t.credit) FROM TransactionPayable t WHERE t.sessionId = :sessionId AND t.status = 'settled'")
    java.math.BigDecimal sumCreditBySessionIdAndStatus(@Param("sessionId") String sessionId);
}