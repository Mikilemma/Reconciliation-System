package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.Dispute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, String> {
    List<Dispute> findByTransactionId(String transactionId);
    List<Dispute> findByDisputeStatus(String status);
    Optional<Dispute> findByTransactionIdAndDisputeStatus(String transactionId, String disputeStatus);
}
