package com.paypulse.common.export;

import com.paypulse.common.error.ErrorCode;
import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import com.paypulse.payment.read.PaymentReadRepository;
import com.paypulse.payment.service.PaymentException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

/**
 * Streams the current filtered payment list as CSV (feature #14, V2, MEM-032).
 *
 * Reads the DB in bounded batches (paypulse.export.batch-size) and writes each
 * batch straight to the HTTP response's Writer as it's fetched — at no point
 * is the full filtered result set held in memory as a single List, so memory
 * usage stays bounded regardless of row count (up to the configured cap).
 *
 * A hard row-count cap (paypulse.export.max-rows) is enforced BEFORE any rows
 * are written — a request whose filter would exceed it is rejected with
 * EXPORT_TOO_LARGE rather than silently truncating the file (a financial
 * export must never look "complete" when it isn't).
 *
 * Owner: M3 (docs/13-WORK-DISTRIBUTION-V2.md)
 */
@Service
public class PaymentCsvExportService {

    private static final String[] HEADER = {
            "id", "sourceAccountId", "destinationAccount", "amount",
            "currency", "status", "errorCode", "createdAt", "updatedAt"
    };

    /** Sort fields this export (and the list endpoint, MEM-033) allow — never trust a raw client-supplied column name. */
    private static final List<String> ALLOWED_SORT_FIELDS = List.of("createdAt", "amount", "status");

    private final PaymentReadRepository paymentReadRepository;
    private final int maxRows;
    private final int batchSize;

    public PaymentCsvExportService(
            PaymentReadRepository paymentReadRepository,
            org.springframework.core.env.Environment env
    ) {
        this.paymentReadRepository = paymentReadRepository;
        this.maxRows = env.getProperty("paypulse.export.max-rows", Integer.class, 50_000);
        this.batchSize = env.getProperty("paypulse.export.batch-size", Integer.class, 500);
    }

    /**
     * Validates the requested sort field against the allow-list.
     * Throws VALIDATION_FAILED (400) for anything not recognized — mirrors the
     * same allow-list used by GET /payments (MEM-033), so an export never
     * silently ignores or exposes an internal/unsafe column name.
     */
    public static void validateSortField(String field) {
        if (field != null && !ALLOWED_SORT_FIELDS.contains(field)) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Unsupported sort field '" + field + "'. Allowed: " + ALLOWED_SORT_FIELDS
            );
        }
    }

    /**
     * Pre-flight check: validates the sort field AND the row-count cap
     * WITHOUT writing anything. Must be called by the controller BEFORE the
     * HTTP response's Content-Type/Content-Disposition headers are set —
     * otherwise a rejection here would corrupt an already-committed
     * "text/csv" response with a JSON error body. Returns the resolved
     * (field, direction) pair so the controller/streamExport don't
     * re-derive defaults inconsistently.
     */
    public String[] validateExportRequest(
            PaymentStatus status,
            String search,
            String sourceAccountId,
            String sortField,
            String sortDirection
    ) {
        String field = (sortField == null || sortField.isBlank()) ? "createdAt" : sortField;
        String direction = normalizeSortDirection(sortDirection);
        validateSortField(field);

        long totalElements = paymentReadRepository.count(status, search, sourceAccountId);

        if (totalElements > maxRows) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.EXPORT_TOO_LARGE,
                    "Filtered result (" + totalElements + " rows) exceeds the export limit of "
                            + maxRows + " — please narrow your filters"
            );
        }

        return new String[] { field, direction };
    }

    /**
     * Streams CSV rows directly to the given writer. Assumes
     * {@link #validateExportRequest} has ALREADY been called successfully by
     * the caller (controller) before any response headers were written.
     */
    public void streamExport(
            PaymentStatus status,
            String search,
            String sourceAccountId,
            String sortField,
            String sortDirection,
            Writer writer
    ) throws IOException {

        String field = (sortField == null || sortField.isBlank()) ? "createdAt" : sortField;
        String direction = normalizeSortDirection(sortDirection);

        Sort sort = Sort.by(Sort.Direction.fromString(direction), field);
        Pageable firstPage = PageRequest.of(0, batchSize, sort);

        Page<Payment> firstResult = paymentReadRepository.search(status, search, sourceAccountId, firstPage);

        writer.write(String.join(",", HEADER));
        writer.write("\n");
        writeRows(writer, firstResult.getContent());

        int totalPages = firstResult.getTotalPages();
        for (int page = 1; page < totalPages; page++) {
            Pageable pageable = PageRequest.of(page, batchSize, sort);
            Page<Payment> result = paymentReadRepository.search(status, search, sourceAccountId, pageable);
            writeRows(writer, result.getContent());
        }

        writer.flush();
    }

    private void writeRows(Writer writer, List<Payment> payments) throws IOException {
        for (Payment p : payments) {
            writer.write(toCsvRow(p));
        }
    }

    private String toCsvRow(Payment p) {
        return String.join(",",
                escape(p.getId()),
                escape(p.getSourceAccountId()),
                escape(p.getDestinationAccount()),
                p.getAmount() == null ? "" : p.getAmount().toPlainString(),
                escape(p.getCurrency()),
                escape(p.getStatus() == null ? null : p.getStatus().name()),
                escape(p.getErrorCode()),
                escape(String.valueOf(p.getCreatedAt())),
                escape(String.valueOf(p.getUpdatedAt()))
        ) + "\n";
    }

    private String normalizeSortDirection(String sortDirection) {
        String direction = (sortDirection == null || sortDirection.isBlank()) ? "desc" : sortDirection;
        try {
            return Sort.Direction.fromString(direction).name().toLowerCase();
        } catch (IllegalArgumentException ex) {
            throw new PaymentException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_FAILED,
                    "Unsupported sort direction '" + direction + "'. Allowed: asc, desc"
            );
        }
    }

    /** RFC-4180-style escaping: quote any field containing a comma, quote, or newline. */
    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}



