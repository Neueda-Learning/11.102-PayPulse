package com.paypulse.analytics.read;

import com.paypulse.payment.PaymentStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class JdbcAnalyticsReadRepository implements AnalyticsReadRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAnalyticsReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long countCreatedBetween(Instant from, Instant to) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from payment where created_at between :from and :to",
                rangeParams(from, to),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Override
    public long countByStatusAndCreatedBetween(PaymentStatus status, Instant from, Instant to) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from payment where status = :status and created_at between :from and :to",
                rangeParams(from, to).addValue("status", status.name()),
                Long.class
        );
        return count == null ? 0L : count;
    }

    @Override
    public Instant maxCreatedAtBetween(Instant from, Instant to) {
        List<Instant> results = jdbcTemplate.query(
                "select max(created_at) from payment where created_at between :from and :to",
                rangeParams(from, to),
                (rs, rowNum) -> {
                    Timestamp timestamp = rs.getTimestamp(1);
                    return timestamp == null ? null : timestamp.toInstant();
                }
        );
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public Double avgProcessingTimeSeconds(Instant from, Instant to) {
        return jdbcTemplate.queryForObject("""
                select avg(timestampdiff(second, p.created_at, h.occurred_at))
                from payment_status_history h
                join payment p on p.id = h.payment_id
                where h.new_status = 'COMPLETED'
                  and p.created_at between :from and :to
                """, rangeParams(from, to), Double.class);
    }

    @Override
    public Map<String, BigDecimal> sumCompletedAmountByCurrency(Instant from, Instant to) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                select p.currency, sum(p.amount) as total_amount
                from payment p
                where p.status = 'COMPLETED'
                  and p.created_at between :from and :to
                group by p.currency
                order by p.currency
                """, rangeParams(from, to), rs -> {
            result.put(rs.getString("currency"), rs.getBigDecimal("total_amount"));
        });
        return result;
    }

    @Override
    public Map<String, Long> topFailureReasons(Instant from, Instant to) {
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query("""
                select p.error_code, count(*) as failure_count
                from payment p
                where p.status = 'FAILED'
                  and p.created_at between :from and :to
                  and p.error_code is not null
                group by p.error_code
                order by failure_count desc, p.error_code asc
                """, rangeParams(from, to), rs -> {
            result.put(rs.getString("error_code"), rs.getLong("failure_count"));
        });
        return result;
    }

    private MapSqlParameterSource rangeParams(Instant from, Instant to) {
        return new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);
    }
}

