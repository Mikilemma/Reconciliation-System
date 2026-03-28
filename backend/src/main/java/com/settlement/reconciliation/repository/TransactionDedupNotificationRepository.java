package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.TransactionDedupNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionDedupNotificationRepository extends JpaRepository<TransactionDedupNotification, String> {
}
