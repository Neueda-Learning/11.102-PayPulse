-- backend/src/main/resources/db/migration/V5__create_notification_log_table.sql

CREATE TABLE notification_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    notification_id     VARCHAR(36) NOT NULL UNIQUE DEFAULT (UUID()),
    recipient_email     VARCHAR(255) NOT NULL,
    recipient_name      VARCHAR(255),
    subject             VARCHAR(500) NOT NULL,
    template_name       VARCHAR(100) NOT NULL,
    template_variables  JSON,
    status              VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    failure_reason      TEXT,
    payment_id          VARCHAR(36),
    event_type          VARCHAR(100) NOT NULL,
    attempts            INT NOT NULL DEFAULT 0,
    last_attempted_at   TIMESTAMP NULL DEFAULT NULL,
    sent_at             TIMESTAMP NULL DEFAULT NULL,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='Audit trail for all email notifications sent by PayPulse';

CREATE INDEX idx_notification_log_payment_id
    ON notification_log(payment_id);

CREATE INDEX idx_notification_log_status
    ON notification_log(status);

CREATE INDEX idx_notification_log_recipient_email
    ON notification_log(recipient_email);

CREATE INDEX idx_notification_log_created_at
    ON notification_log(created_at DESC);