package org.acme.ruleunits.definition;

import java.util.Objects;

/**
 * Shared DRL template text and stable template key for the early parameterization model.
 * Rule-specific conditions and actions remain separate structured values.
 */
public record RuleTemplate(String templateKey, String drlTemplate) {

    public RuleTemplate {
        Objects.requireNonNull(templateKey);
        Objects.requireNonNull(drlTemplate);
    }
}
