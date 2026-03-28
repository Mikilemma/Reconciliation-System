
package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findBySessionId(String sessionId);

    List<Transaction> findBySessionIdIn(List<String> sessionIds);

    List<Transaction> findBySessionIdAndSource(String sessionId, String source);

    @Query("SELECT t FROM Transaction t WHERE t.sessionId = :sessionId AND t.source = :source AND t.issuer IS NOT NULL")
    List<Transaction> findBySessionIdAndSourceWithIssuer(@Param("sessionId") String sessionId, @Param("source") String source);

    @Query("SELECT t FROM Transaction t WHERE t.sessionId = :sessionId AND t.stanNo = :stanNo")
    Optional<Transaction> findBySessionIdAndStanNo(@Param("sessionId") String sessionId, @Param("stanNo") String stanNo);

    @Query("SELECT t FROM Transaction t WHERE t.sessionId = :sessionId AND t.transRef = :transRef")
    List<Transaction> findBySessionIdAndTransRef(@Param("sessionId") String sessionId, @Param("transRef") String transRef);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.sessionId = :sessionId")
    long countBySessionId(@Param("sessionId") String sessionId);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.sessionId = :sessionId AND t.status = :status")
    long countBySessionIdAndStatus(@Param("sessionId") String sessionId, @Param("status") String status);

    List<Transaction> findByStanNo(String stanNo);
    List<Transaction> findByTransRef(String transRef);
    Optional<Transaction> findByDedupeKey(String dedupeKey);
}
