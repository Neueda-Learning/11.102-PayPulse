-- V2: Account table
-- Owner: M1 (see docs/13-WORK-DISTRIBUTION.md)

CREATE TABLE IF NOT EXISTS account (
    id              VARCHAR(36)        NOT NULL,
    label           VARCHAR(100)    NOT NULL,
    account_number  VARCHAR(20)     NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT uq_account_number UNIQUE (account_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Add FK from payment to account now that account table exists
ALTER TABLE payment
    ADD CONSTRAINT fk_payment_source_account
    FOREIGN KEY (source_account_id) REFERENCES account (id);

