// backend/src/main/java/com/paypulse/notification/template/EmailTemplateEngine.java

package com.paypulse.notification.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;

/**
 * Thin wrapper around Thymeleaf's TemplateEngine.
 *
 * Resolves templates from: resources/templates/email/{templateName}.html
 * All variables passed via the map are injected into the Thymeleaf context,
 * making them available as ${variableName} inside templates.
 */
@Component
public class EmailTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateEngine.class);
    private static final String TEMPLATE_PREFIX = "email/";

    private final TemplateEngine thymeleafEngine;

    public EmailTemplateEngine(TemplateEngine thymeleafEngine) {
        this.thymeleafEngine = thymeleafEngine;
    }

    /**
     * Renders an HTML email body from a Thymeleaf template.
     *
     * @param templateName short name of the template (e.g. "payment-completed")
     * @param variables    arbitrary key-value pairs injected into the template context
     * @return rendered HTML string ready for inclusion in a MIME message
     */
    public String render(String templateName, Map<String, Object> variables) {
        // MUST BE ADDED HERE, before log.debug
        if (variables == null) {
            variables = new java.util.HashMap<>();
        }

        String fullTemplatePath = TEMPLATE_PREFIX + templateName;
        log.debug("Rendering email template '{}' with {} variables",
                fullTemplatePath, variables.size());

        Context ctx = new Context(Locale.ENGLISH);
        variables.forEach(ctx::setVariable);

        try {
            return thymeleafEngine.process(fullTemplatePath, ctx);
        } catch (Exception ex) {
            log.error("Failed to render template '{}': {}", fullTemplatePath, ex.getMessage(), ex);
            throw new IllegalStateException(
                    "Email template rendering failed for: " + fullTemplatePath, ex);
        }
    }
}