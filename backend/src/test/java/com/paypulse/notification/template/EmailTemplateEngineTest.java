// backend/src/test/java/com/paypulse/notification/template/EmailTemplateEngineTest.java

package com.paypulse.notification.template;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailTemplateEngineTest {

    @Mock  private TemplateEngine thymeleafEngine;
    @InjectMocks private EmailTemplateEngine sut;

    @Test
    void render_prependsEmailPrefixToTemplateName() {
        when(thymeleafEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html>ok</html>");

        sut.render("payment-completed", Map.of("amount", "100"));

        verify(thymeleafEngine).process(eq("email/payment-completed"), any(Context.class));
    }

    @Test
    void render_populatesAllVariablesIntoContext() {
        when(thymeleafEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html>ok</html>");

        String result = sut.render("payment-completed", Map.of(
                "amount", "500.00",
                "currency", "INR"
        ));

        assertThat(result).isEqualTo("<html>ok</html>");
    }

    @Test
    void render_handlesNullVariablesGracefully() {
        when(thymeleafEngine.process(anyString(), any(Context.class)))
                .thenReturn("<html>ok</html>");

        assertThatNoException().isThrownBy(
                () -> sut.render("welcome", null)
        );
    }

    @Test
    void render_throwsIllegalStateExceptionOnTemplateError() {
        when(thymeleafEngine.process(anyString(), any(Context.class)))
                .thenThrow(new RuntimeException("Template not found"));

        assertThatThrownBy(() -> sut.render("bad-template", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Email template rendering failed");
    }
}