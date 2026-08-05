package com.paypulse.payment.service.states;

import com.paypulse.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancelledStateTest {

    private final CancelledState state = new CancelledState();

    @Test
    void status_isCancelled() {
        assertThat(state.status()).isEqualTo(PaymentStatus.CANCELLED);
    }

    @Test
    void cancelled_isTerminal_noFurtherTransitionsAllowed() {
        assertThatThrownBy(state::validate).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(state::send).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(state::complete).isInstanceOf(InvalidStatusTransitionException.class);
        assertThatThrownBy(state::cancel).isInstanceOf(InvalidStatusTransitionException.class);
    }
}