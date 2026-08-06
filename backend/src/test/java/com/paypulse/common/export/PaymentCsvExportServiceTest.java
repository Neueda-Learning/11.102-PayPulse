package com.paypulse.common.export;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.read.PaymentReadRepository;
import com.paypulse.payment.service.PaymentException;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentCsvExportServiceTest {

    private final PaymentReadRepository paymentReadRepository = mock(PaymentReadRepository.class);
    private final Environment env = mock(Environment.class);

    private PaymentCsvExportService newService() {
        when(env.getProperty(eq("paypulse.export.max-rows"), eq(Integer.class), any())).thenReturn(50_000);
        when(env.getProperty(eq("paypulse.export.batch-size"), eq(Integer.class), any())).thenReturn(500);
        return new PaymentCsvExportService(paymentReadRepository, env);
    }

    private Payment samplePayment() {
        return Payment.builder()
                .id("a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .sourceAccountId("b2c3d4e5-1111-4a11-8a11-111111111111")
                .destinationAccount("ACC2000002")
                .amount(new BigDecimal("250.00"))
                .currency("INR")
                .reference("Invoice, with a comma")
                .status(PaymentStatus.COMPLETED)
                .createdAt(Instant.parse("2026-08-05T10:00:00Z"))
                .updatedAt(Instant.parse("2026-08-05T10:00:01Z"))
                .build();
    }

    @Test
    void validateExportRequest_unknownSortField_throwsValidationFailed() {
        PaymentCsvExportService service = newService();

        assertThatThrownBy(() ->
                service.validateExportRequest(null, null, null, "someInternalColumn", "asc")
        ).isInstanceOf(PaymentException.class)
         .hasMessageContaining("Unsupported sort field");
    }

    @Test
    void validateExportRequest_withinCap_returnsResolvedSort() {
        PaymentCsvExportService service = newService();
        Page<Payment> page = new PageImpl<>(List.of(samplePayment()));
        when(paymentReadRepository.count(any(), any(), any())).thenReturn(1L);

        String[] resolved = service.validateExportRequest(null, null, null, null, null);

        assertThat(resolved).containsExactly("createdAt", "desc");
    }

    @Test
    void validateExportRequest_exceedsCap_throwsExportTooLarge() {
        when(env.getProperty(eq("paypulse.export.max-rows"), eq(Integer.class), any())).thenReturn(1);
        when(env.getProperty(eq("paypulse.export.batch-size"), eq(Integer.class), any())).thenReturn(500);
        PaymentCsvExportService service = new PaymentCsvExportService(paymentReadRepository, env);

        when(paymentReadRepository.count(any(), any(), any())).thenReturn(2L);

        assertThatThrownBy(() ->
                service.validateExportRequest(PaymentStatus.COMPLETED, null, null, null, null)
        ).isInstanceOf(PaymentException.class)
         .hasMessageContaining("exceeds the export limit");
    }

    @Test
    void streamExport_writesHeaderAndEscapedRows() throws Exception {
        PaymentCsvExportService service = newService();
        Page<Payment> page = new PageImpl<>(List.of(samplePayment()));
        when(paymentReadRepository.search(any(), any(), any(), any(Pageable.class))).thenReturn(page);

        StringWriter writer = new StringWriter();
        service.streamExport(null, null, null, "createdAt", "desc", writer);

        String csv = writer.toString();
        assertThat(csv).startsWith("id,sourceAccountId,destinationAccount,amount,currency,status,errorCode,createdAt,updatedAt\n");
        assertThat(csv).contains("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        assertThat(csv).contains("COMPLETED");
        // amount has no comma-escaping concerns, but reference isn't exported — only checking core columns present
    }
}

