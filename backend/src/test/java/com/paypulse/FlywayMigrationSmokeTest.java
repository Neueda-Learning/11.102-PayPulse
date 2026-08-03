package com.paypulse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: full Spring context loads and all Flyway migrations
 * (V1 payment, V2 account+FK, V3 seed data) apply cleanly.
 */
@SpringBootTest
class FlywayMigrationSmokeTest {

    @Test
    void contextLoads_andMigrationsApplyCleanly() {
        // If this test runs at all without throwing, Flyway + Hibernate
        // validation + context startup all succeeded.
    }
}