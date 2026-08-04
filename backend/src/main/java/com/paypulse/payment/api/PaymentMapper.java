package com.paypulse.payment.api;
import com.paypulse.payment.api.dto.PaymentHistoryResponse;
import com.paypulse.payment.domain.PaymentStatusHistory;
import com.paypulse.payment.api.dto.CreatePaymentRequest;
import com.paypulse.payment.api.dto.PaymentResponse;
import com.paypulse.payment.domain.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Entity <-> DTO mapping contract shared with M4.
 */
@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "errorCode", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "idempotencyKey", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "forcedFailureStage", ignore = true)
    Payment toEntity(CreatePaymentRequest request);

    @Mapping(target = "triggeredBy", expression = "java(history.getTriggeredBy() != null ? history.getTriggeredBy().name() : null)")
    PaymentHistoryResponse toHistoryResponse(PaymentStatusHistory history);

    PaymentResponse toResponse(Payment payment);
}

