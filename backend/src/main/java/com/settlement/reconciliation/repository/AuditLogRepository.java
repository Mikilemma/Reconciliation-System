package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
    List<AuditLog> findByDisputeIdOrderByCreatedAtDesc(String disputeId);
}
