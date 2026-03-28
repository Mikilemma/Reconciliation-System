package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.ReconciliationSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReconciliationSessionRepository extends JpaRepository<ReconciliationSession, String> {
    Optional<ReconciliationSession> findBySettlementDate(String settlementDate);
    java.util.List<ReconciliationSession> findAllBySettlementDate(String settlementDate);
}
