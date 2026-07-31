-- V1: Initial schema — Payment and PaymentStatusHistory tables
-- Owner: M1 (see docs/13-WORK-DISTRIBUTION.md)
-- All DDL is owned by Flyway — Hibernate ddl-auto=validate only

CREATE TABLE IF NOT EXISTS payment (
    id               CHAR(36)        NOT NULL,
    source_account_id CHAR(36)       NOT NULL,
    destination_account VARCHAR(20)  NOT NULL,
    amount           DECIMAL(19, 2)  NOT NULL,
    currency         VARCHAR(3)      NOT NULL,
    reference        VARCHAR(255)    NULL,
    status           VARCHAR(20)     NOT NULL,
    error_code       VARCHAR(50)     NULL,
    error_message    VARCHAR(500)    NULL,
    idempotency_key  VARCHAR(100)    NULL,
    created_at       TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version          BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment PRIMARY KEY (id),
    CONSTRAINT uq_payment_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Indexes for NFR-7/10 (filter/search/analytics queries)
CREATE INDEX idx_payment_status       ON payment (status);
CREATE INDEX idx_payment_created_at   ON payment (created_at);
CREATE INDEX idx_payment_src_account  ON payment (source_account_id);

CREATE TABLE IF NOT EXISTS payment_status_history (
    id               BIGINT          NOT NULL AUTO_INCREMENT,
    payment_id       CHAR(36)        NOT NULL,
    previous_status  VARCHAR(20)     NULL,
    new_status       VARCHAR(20)     NOT NULL,
    error_code       VARCHAR(50)     NULL,
    error_message    VARCHAR(500)    NULL,
    triggered_by     VARCHAR(20)     NOT NULL DEFAULT 'SYSTEM',
    occurred_at      TIMESTAMP(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT pk_payment_status_history PRIMARY KEY (id),
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payment (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_history_payment_id   ON payment_status_history (payment_id);
CREATE INDEX idx_history_occurred_at  ON payment_status_history (occurred_at);

