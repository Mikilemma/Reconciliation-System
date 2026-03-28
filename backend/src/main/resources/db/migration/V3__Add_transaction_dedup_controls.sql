ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(255) NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_transactions_dedupe_key
    ON transactions (dedupe_key);

CREATE TABLE IF NOT EXISTS transaction_dedup_notifications (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36),
    file_id VARCHAR(36),
    dedupe_key VARCHAR(255) NOT NULL,
    stan_no VARCHAR(100),
    trans_ref VARCHAR(100),
    txn_amount DECIMAL(15,2),
    existing_transaction_id VARCHAR(36) NOT NULL,
    existing_status VARCHAR(50),
    reason VARCHAR(50) NOT NULL,
    message TEXT,
    created_at DATETIME NOT NULL,
    INDEX idx_tdn_session (session_id),
    INDEX idx_tdn_file (file_id),
    INDEX idx_tdn_existing_tx (existing_transaction_id),
    INDEX idx_tdn_created_at (created_at)
);
