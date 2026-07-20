package org.acme.ruleunits.loading;

import java.util.List;

/**
 * Aggregates structural or catalog-validation failures for a loaded candidate. A candidate
 * producing this exception must never reach DRL assembly or publication.
 */
public final class RuleDefinitionValidationException extends RuntimeException {
    private final List<String> errors;

    public RuleDefinitionValidationException(List<String> errors) {
        super("Rule-set validation failed: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }
}
