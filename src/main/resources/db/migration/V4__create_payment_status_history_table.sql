CREATE TABLE payment_status_history (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    payment_id VARCHAR(36) NOT NULL,
    previous_status VARCHAR(20) NULL,
    new_status VARCHAR(20) NOT NULL,
    error_code VARCHAR(50) NULL,
    error_message VARCHAR(500) NULL,
    triggered_by VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_status_history_payment
        FOREIGN KEY (payment_id) REFERENCES payment(id)
        ON DELETE CASCADE,

    INDEX idx_psh_payment_id (payment_id),
    INDEX idx_psh_occurred_at (occurred_at),
    INDEX idx_psh_payment_occurred (payment_id, occurred_at)
);