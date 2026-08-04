package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentStateFactoryTest {

    private final PaymentStateFactory factory = new PaymentStateFactory();

    @Test
    void created_allowsValidate_only() {
        PaymentState created = factory.from(PaymentStatus.CREATED);

        assertThat(created.validate()).isEqualTo(PaymentStatus.VALIDATED);
        assertThatThrownBy(created::send).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(created::complete).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void validated_allowsSend_only() {
        PaymentState validated = factory.from(PaymentStatus.VALIDATED);

        assertThat(validated.send()).isEqualTo(PaymentStatus.SENT);
        assertThatThrownBy(validated::validate).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(validated::complete).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void sent_allowsComplete_only() {
        PaymentState sent = factory.from(PaymentStatus.SENT);

        assertThat(sent.complete()).isEqualTo(PaymentStatus.COMPLETED);
        assertThatThrownBy(sent::validate).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(sent::send).isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void completed_and_failed_areTerminal() {
        PaymentState completed = factory.from(PaymentStatus.COMPLETED);
        PaymentState failed = factory.from(PaymentStatus.FAILED);

        assertThatThrownBy(completed::validate).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(completed::send).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(completed::complete).isInstanceOf(InvalidStatusTransitionException.class);

        assertThatThrownBy(failed::validate).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(failed::send).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(failed::complete).isInstanceOf(InvalidStatusTransitionException.class);
    }
}

