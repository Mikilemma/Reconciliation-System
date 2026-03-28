package com.settlement.reconciliation.repository;

import com.settlement.reconciliation.model.UploadedFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UploadedFileRepository extends JpaRepository<UploadedFile, String> {
    List<UploadedFile> findBySessionId(String sessionId);
    List<UploadedFile> findBySettlementDate(String settlementDate);
    
    @Query("SELECT u FROM UploadedFile u WHERE u.sessionId = :sessionId AND u.fileContent IS NOT NULL AND u.fileContent LIKE '%Member Net Position%'")
    Optional<UploadedFile> findTsehaySummaryBySessionId(@Param("sessionId") String sessionId);
}
