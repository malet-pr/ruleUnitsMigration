package org.acme.ruleunits.action;

import java.util.Objects;

/**
 * Queued correction command that keeps the first active matching occurrence in work-order order
 * and deactivates the remaining duplicates with rule attribution.
 */
public record DeactivateActivitiesExceptOne(
        String activityCode,
        String ruleType,
        String ruleName
) implements RuleAction {
    public DeactivateActivitiesExceptOne {
        Objects.requireNonNull(activityCode);
        Objects.requireNonNull(ruleType);
        Objects.requireNonNull(ruleName);
    }
}
