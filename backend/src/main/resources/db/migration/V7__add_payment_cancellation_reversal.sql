ALTER TABLE payment
    ADD COLUMN reversed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN reversal_payment_id VARCHAR(36) NULL,
    ADD COLUMN reversal_of_payment_id VARCHAR(36) NULL;

CREATE INDEX idx_payment_reversal_of ON payment (reversal_of_payment_id);