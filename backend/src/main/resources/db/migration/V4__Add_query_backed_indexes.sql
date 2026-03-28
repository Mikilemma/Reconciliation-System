-- Add only indexes backed by real query patterns used in repositories/services.

-- reconciliation_results: frequent predicates on session_id + status and
-- anti-join checks on (session_id, transaction_ref|stan, status)
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'reconciliation_results'
      AND index_name = 'idx_result_session_status'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_result_session_status ON reconciliation_results (session_id, status)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'reconciliation_results'
      AND index_name = 'idx_result_session_trans_ref_status'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_result_session_trans_ref_status ON reconciliation_results (session_id, transaction_ref, status)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'reconciliation_results'
      AND index_name = 'idx_result_session_stan_status'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_result_session_stan_status ON reconciliation_results (session_id, stan, status)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- uploaded_files: repository lookup by session_id
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'uploaded_files'
      AND index_name = 'idx_uploaded_files_session_id'
);
SET @sql := IF(@idx_exists = 0,
    'CREATE INDEX idx_uploaded_files_session_id ON uploaded_files (session_id)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
