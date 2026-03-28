package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.TransactionSwitch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionSwitchRepository extends JpaRepository<TransactionSwitch, String> {
    
    List<TransactionSwitch> findBySessionId(String sessionId);
    
    List<TransactionSwitch> findByFileId(String fileId);
    
    List<TransactionSwitch> findByStatus(String status);
    
    Optional<TransactionSwitch> findByStan(String stan);
    
    Optional<TransactionSwitch> findByRefNum(String refNum);
    
    List<TransactionSwitch> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT t FROM TransactionSwitch t WHERE t.sessionId = :sessionId AND t.status = :status")
    List<TransactionSwitch> findBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") String status);
    
    @Query("SELECT COUNT(t) FROM TransactionSwitch t WHERE t.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);
    
    @Query("SELECT COUNT(t) FROM TransactionSwitch t WHERE t.sessionId = :sessionId AND t.status = :status")
    long countBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") String status);
}