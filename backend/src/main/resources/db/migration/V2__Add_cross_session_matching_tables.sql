-- Create unresolved_transactions table
CREATE TABLE unresolved_transactions (
    id VARCHAR(36) PRIMARY KEY,
    stan VARCHAR(50),
    transaction_ref VARCHAR(100),
    amount DECIMAL(15,2),
    transaction_date TIMESTAMP,
    terminal_id VARCHAR(50),
    status VARCHAR(20) NOT NULL,
    discrepancy_type VARCHAR(100),
    details TEXT,
    original_session_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    resolved_by_session_id VARCHAR(36),
    source_files VARCHAR(500),
    
    INDEX idx_unresolved_status (status),
    INDEX idx_unresolved_resolved (resolved_at),
    INDEX idx_unresolved_session (original_session_id),
    INDEX idx_unresolved_stan (stan),
    INDEX idx_unresolved_amount (amount),
    INDEX idx_unresolved_terminal (terminal_id),
    INDEX idx_unresolved_date (transaction_date)
);

-- Create cross_session_matches table
CREATE TABLE cross_session_matches (
    id VARCHAR(36) PRIMARY KEY,
    original_transaction_id VARCHAR(36) NOT NULL,
    original_session_id VARCHAR(36) NOT NULL,
    matched_transaction_id VARCHAR(36) NOT NULL,
    matched_session_id VARCHAR(36) NOT NULL,
    match_type VARCHAR(20) NOT NULL,
    confidence DOUBLE NOT NULL,
    matched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_by VARCHAR(20) NOT NULL,
    match_notes TEXT,
    review_status VARCHAR(20) DEFAULT 'PENDING',
    reviewed_by VARCHAR(100),
    reviewed_at TIMESTAMP,
    rejection_reason VARCHAR(500),
    
    INDEX idx_matches_original_tx (original_transaction_id),
    INDEX idx_matches_matched_tx (matched_transaction_id),
    INDEX idx_matches_original_session (original_session_id),
    INDEX idx_matches_matched_session (matched_session_id),
    INDEX idx_matches_type (match_type),
    INDEX idx_matches_confidence (confidence),
    INDEX idx_matches_review_status (review_status),
    INDEX idx_matches_resolved_by (resolved_by),
    INDEX idx_matches_date (matched_at),
    
    -- Unique constraint to prevent duplicate matches
    UNIQUE KEY uk_transaction_pair (
        LEAST(original_transaction_id, matched_transaction_id),
        GREATEST(original_transaction_id, matched_transaction_id)
    )
);

-- Add foreign key constraints (if sessions table exists)
-- ALTER TABLE unresolved_transactions 
-- ADD CONSTRAINT fk_unresolved_session 
-- FOREIGN KEY (original_session_id) REFERENCES reconciliation_sessions(id);

-- ALTER TABLE cross_session_matches 
-- ADD CONSTRAINT fk_match_original_session 
-- FOREIGN KEY (original_session_id) REFERENCES reconciliation_sessions(id);

-- ALTER TABLE cross_session_matches 
-- ADD CONSTRAINT fk_match_matched_session 
-- FOREIGN KEY (matched_session_id) REFERENCES reconciliation_sessions(id);

-- Create view for unresolved transaction statistics
CREATE VIEW v_unresolved_stats AS
SELECT 
    status,
    COUNT(*) as count,
    COUNT(CASE WHEN resolved_at IS NULL THEN 1 END) as unresolved_count,
    COUNT(CASE WHEN resolved_at IS NOT NULL THEN 1 END) as resolved_count,
    DATE(createdAt) as created_date
FROM unresolved_transactions 
GROUP BY status, DATE(createdAt);

-- Create view for cross-session match statistics
CREATE VIEW v_cross_session_match_stats AS
SELECT 
    match_type,
    resolved_by,
    review_status,
    COUNT(*) as total_matches,
    AVG(confidence) as avg_confidence,
    MIN(confidence) as min_confidence,
    MAX(confidence) as max_confidence,
    DATE(matched_at) as match_date
FROM cross_session_matches 
GROUP BY match_type, resolved_by, review_status, DATE(matched_at);

-- Create procedure to clean up old resolved transactions (older than 90 days)
DELIMITER //
CREATE PROCEDURE CleanupOldResolvedTransactions()
BEGIN
    DELETE FROM unresolved_transactions 
    WHERE resolved_at IS NOT NULL 
    AND resolved_at < DATE_SUB(NOW(), INTERVAL 90 DAY);
    
    DELETE FROM cross_session_matches 
    WHERE matched_at < DATE_SUB(NOW(), INTERVAL 90 DAY)
    AND review_status = 'APPROVED';
END //
DELIMITER ;

-- Create procedure to get cross-session matching summary
DELIMITER //
CREATE PROCEDURE GetCrossSessionMatchingSummary()
BEGIN
    SELECT 
        'Unresolved Transactions' as metric_type,
        COUNT(*) as total_count,
        COUNT(CASE WHEN resolved_at IS NULL THEN 1 END) as unresolved_count,
        COUNT(CASE WHEN resolved_at IS NOT NULL THEN 1 END) as resolved_count
    FROM unresolved_transactions
    
    UNION ALL
    
    SELECT 
        'Cross-Session Matches' as metric_type,
        COUNT(*) as total_count,
        COUNT(CASE WHEN review_status = 'PENDING' THEN 1 END) as pending_count,
        COUNT(CASE WHEN review_status = 'APPROVED' THEN 1 END) as approved_count
    FROM cross_session_matches
    
    UNION ALL
    
    SELECT 
        'High Confidence Matches (>95%)' as metric_type,
        COUNT(*) as total_count,
        0 as unresolved_count,
        COUNT(*) as resolved_count
    FROM cross_session_matches 
    WHERE confidence >= 95.0;
END //
DELIMITER ;
