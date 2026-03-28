package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.TransactionAtm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionAtmRepository extends JpaRepository<TransactionAtm, String> {
    Optional<TransactionAtm> findByStanNoAndSessionId(String stanNo, String sessionId);
    List<TransactionAtm> findBySessionId(String sessionId);
    List<TransactionAtm> findBySessionIdIn(List<String> sessionIds);
    List<TransactionAtm> findByFileId(String fileId);
}
