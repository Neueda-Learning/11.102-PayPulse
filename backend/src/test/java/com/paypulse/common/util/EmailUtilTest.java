// backend/src/test/java/com/paypulse/common/util/EmailUtilTest.java

package com.paypulse.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class EmailUtilTest {

    @Test
    void maskAccount_masksAllButLast4AndAdjacentChar() {
        String masked = EmailUtil.maskAccount("ACC-INR-001");
        assertThat(masked).endsWith("001");
        assertThat(masked).startsWith("XXXX");
    }

    @Test
    void maskAccount_returnsNullWhenInputIsNull() {
        assertThat(EmailUtil.maskAccount(null)).isNull();
    }

    @Test
    void maskAccount_returnsSameWhenLengthIs4OrLess() {
        assertThat(EmailUtil.maskAccount("1234")).isEqualTo("1234");
        assertThat(EmailUtil.maskAccount("AB")).isEqualTo("AB");
    }

    @Test
    void format_returnsFormattedDate() {
        LocalDateTime dt = LocalDateTime.of(2025, 6, 25, 15, 45, 0);
        String result = EmailUtil.format(dt);
        assertThat(result).contains("2025");
        assertThat(result).contains("UTC");
    }

    @Test
    void format_returnsDashWhenNull() {
        assertThat(EmailUtil.format(null)).isEqualTo("—");
    }

    @Test
    void shortId_returns8CharUppercasePlusEllipsis() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        String result = EmailUtil.shortId(uuid);
        assertThat(result).hasSize(9); // 8 + "…"
        assertThat(result).startsWith("550E8400");
    }

    @Test
    void paymentCompletedVars_containsAllExpectedKeys() {
        Map<String, Object> vars = EmailUtil.paymentCompletedVars(
                UUID.randomUUID(),
                "REF-001",
                "1500.00",
                "INR",
                "ACC-INR-001",
                "EXT-9876",
                LocalDateTime.now()
        );

        assertThat(vars).containsKeys(
                "paymentId", "referenceId", "amount", "currency",
                "sourceAccount", "destinationAccount", "completedAt"
        );
    }

    @Test
    void paymentFailedVars_defaultsErrorCodeToUnknownWhenNull() {
        Map<String, Object> vars = EmailUtil.paymentFailedVars(
                UUID.randomUUID(), "REF-002",
                "500.00", "USD",
                "Insufficient funds", null,
                LocalDateTime.now()
        );
        assertThat(vars).containsEntry("errorCode", "UNKNOWN");
    }

    @Test
    void paymentCreatedVars_masksAccountNumbers() {
        Map<String, Object> vars = EmailUtil.paymentCreatedVars(
                UUID.randomUUID(), "REF-003",
                "200.00", "INR",
                "ACC-INR-001", "EXT-1234",
                LocalDateTime.now()
        );
        assertThat(vars.get("sourceAccount").toString()).startsWith("XXXX");
        assertThat(vars.get("destinationAccount").toString()).startsWith("XXXX");
    }
}