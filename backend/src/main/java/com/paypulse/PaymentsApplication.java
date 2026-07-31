package com.paypulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * PayPulse — Payments Processing System
 *
 * Entry point. Package structure follows package-by-feature (see docs/05-ARCHITECTURE.md §2):
 *
 *   com.paypulse
 *   ├── payment/       (M2 state machine + M3 create + M4 read endpoints)
 *   ├── account/       (M1 accounts entity + M4 account read API)
 *   ├── analytics/     (M4 KPI dashboard + trend)
 *   └── common/        (error handling, rate limiting, resilience, idempotency, config)
 */
@SpringBootApplication
public class PaymentsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentsApplication.class, args);
    }
}

