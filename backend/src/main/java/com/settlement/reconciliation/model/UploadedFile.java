package com.settlement.reconciliation.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "uploaded_files", indexes = {
    @Index(name = "idx_uploaded_files_session_id", columnList = "session_id")
})
public class UploadedFile {

    @Id
    @Column(columnDefinition = "VARCHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String filename;

    @Column(name = "file_type", nullable = false, columnDefinition = "TEXT")
    private String fileType;

    @Column(nullable = false)
    private long size;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "record_count", nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private int recordCount;

    @Column(nullable = false, columnDefinition = "TEXT")
    @org.hibernate.annotations.ColumnDefault("'uploaded'")
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "session_id", columnDefinition = "VARCHAR(36)")
    private String sessionId;

    @Column(name = "settlement_date", columnDefinition = "VARCHAR(50)")
    private String settlementDate;

    // Store file content for TSEHAY summary files
    @Lob
    @Column(name = "file_content", columnDefinition = "LONGTEXT")
    private String fileContent;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }
}
