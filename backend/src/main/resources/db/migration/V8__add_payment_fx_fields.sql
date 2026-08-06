ALTER TABLE payment
    ADD COLUMN target_currency VARCHAR(3) NULL AFTER currency,
    ADD COLUMN converted_amount DECIMAL(19, 2) NULL AFTER target_currency,
    ADD COLUMN fx_rate DECIMAL(19, 6) NULL AFTER converted_amount;

UPDATE payment
SET target_currency = currency,
    converted_amount = amount,
    fx_rate = 1.000000
WHERE target_currency IS NULL;

ALTER TABLE payment
    MODIFY COLUMN target_currency VARCHAR(3) NOT NULL,
    MODIFY COLUMN converted_amount DECIMAL(19, 2) NOT NULL,
    MODIFY COLUMN fx_rate DECIMAL(19, 6) NOT NULL;

CREATE INDEX idx_payment_target_currency ON payment (target_currency);

