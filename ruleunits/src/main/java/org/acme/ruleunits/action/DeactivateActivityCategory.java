package org.acme.ruleunits.action;

import java.util.Objects;

/**
 * Queued command that deactivates active occurrences in one category. Inactive occurrences are
 * intentionally outside the match, consistent with the corrected RA3 category semantics.
 */
public record DeactivateActivityCategory(
        String category,
        String ruleType,
        String ruleName
) implements RuleAction {
    public DeactivateActivityCategory {
        Objects.requireNonNull(category);
        Objects.requireNonNull(ruleType);
        Objects.requireNonNull(ruleName);
    }
}
