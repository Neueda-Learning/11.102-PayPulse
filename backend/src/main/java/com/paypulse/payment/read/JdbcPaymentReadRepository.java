package com.paypulse.payment.read;

import com.paypulse.payment.PaymentStatus;
import com.paypulse.payment.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcPaymentReadRepository implements PaymentReadRepository {

    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "p.created_at",
            "amount", "p.amount",
            "status", "p.status"
    );

    private static final RowMapper<Payment> PAYMENT_ROW_MAPPER = JdbcPaymentReadRepository::mapPayment;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Page<Payment> search(PaymentStatus status, String search, String sourceAccountId, Pageable pageable) {
        MapSqlParameterSource params = baseParams(status, search, sourceAccountId)
                .addValue("limit", pageable.getPageSize())
                .addValue("offset", pageable.getOffset());

        String sql = """
                select
                    p.id,
                    p.source_account_id,
                    p.destination_account,
                    p.amount,
                    p.currency,
                    p.target_currency,
                    p.converted_amount,
                    p.fx_rate,
                    p.reference as payment_reference,
                    p.status,
                    p.error_code,
                    p.error_message,
                    p.idempotency_key,
                    p.created_at,
                    p.updated_at,
                    p.reversed,
                    p.reversal_payment_id,
                    p.reversal_of_payment_id,
                    p.version
                from payment p
                """ + whereClause() + orderByClause(pageable.getSort()) + " limit :limit offset :offset";

        List<Payment> content = jdbcTemplate.query(sql, params, PAYMENT_ROW_MAPPER);
        long total = count(status, search, sourceAccountId);
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public long count(PaymentStatus status, String search, String sourceAccountId) {
        String sql = "select count(*) from payment p " + whereClause();
        Long total = jdbcTemplate.queryForObject(sql, baseParams(status, search, sourceAccountId), Long.class);
        return total == null ? 0L : total;
    }

    private MapSqlParameterSource baseParams(PaymentStatus status, String search, String sourceAccountId) {
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        String normalizedAccountId = (sourceAccountId == null || sourceAccountId.isBlank()) ? null : sourceAccountId.trim();
        return new MapSqlParameterSource()
                .addValue("status", status != null ? status.name() : null)
                .addValue("search", normalizedSearch)
                .addValue("sourceAccountId", normalizedAccountId);
    }

    private String whereClause() {
        return """
                where (:status is null or p.status = :status)
                  and (:sourceAccountId is null or p.source_account_id = :sourceAccountId)
                  and (:search is null or p.id like concat('%', :search, '%') or coalesce(p.reference, '') like concat('%', :search, '%'))
                """;
    }

    private String orderByClause(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return " order by p.created_at desc";
        }

        StringBuilder orderBy = new StringBuilder(" order by ");
        boolean first = true;
        for (Sort.Order order : sort) {
            String column = SORT_COLUMNS.get(order.getProperty());
            if (column == null) {
                continue;
            }
            if (!first) {
                orderBy.append(", ");
            }
            orderBy.append(column).append(order.isAscending() ? " asc" : " desc");
            first = false;
        }

        return first ? " order by p.created_at desc" : orderBy.toString();
    }

    private static Payment mapPayment(ResultSet rs, int rowNum) throws SQLException {
        return Payment.builder()
                .id(rs.getString("id"))
                .sourceAccountId(rs.getString("source_account_id"))
                .destinationAccount(rs.getString("destination_account"))
                .amount(rs.getBigDecimal("amount"))
                .currency(rs.getString("currency"))
                .targetCurrency(rs.getString("target_currency"))
                .convertedAmount(rs.getBigDecimal("converted_amount"))
                .fxRate(rs.getBigDecimal("fx_rate"))
                .reference(rs.getString("payment_reference"))
                .status(readStatus(rs, "status"))
                .errorCode(rs.getString("error_code"))
                .errorMessage(rs.getString("error_message"))
                .idempotencyKey(rs.getString("idempotency_key"))
                .createdAt(toInstant(rs.getTimestamp("created_at")))
                .updatedAt(toInstant(rs.getTimestamp("updated_at")))
                .reversed(rs.getBoolean("reversed"))
                .reversalPaymentId(rs.getString("reversal_payment_id"))
                .reversalOfPaymentId(rs.getString("reversal_of_payment_id"))
                .version(rs.getLong("version"))
                .build();
    }

    private static PaymentStatus readStatus(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        return value == null ? null : PaymentStatus.valueOf(value);
    }

    private static java.time.Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

