package org.acme.ruleunits.action;

import java.util.Objects;

/**
 * Queued command that deactivates every active occurrence in the current stage input. This
 * includes occurrences created and saved by an earlier rule stage.
 */
public record DeactivateAllActivities(
        String ruleType,
        String ruleName
) implements RuleAction {
    public DeactivateAllActivities {
        Objects.requireNonNull(ruleType);
        Objects.requireNonNull(ruleName);
    }
}
