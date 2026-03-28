package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.ReconciliationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationResultRepository extends JpaRepository<ReconciliationResult, String> {
    List<ReconciliationResult> findBySessionId(String sessionId);
    List<ReconciliationResult> findByStan(String stan);
    List<ReconciliationResult> findByTransactionRef(String transactionRef);
}
