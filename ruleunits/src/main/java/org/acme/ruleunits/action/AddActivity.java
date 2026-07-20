package org.acme.ruleunits.action;

import java.util.Objects;

/**
 * Queued command requesting one rule-created activity occurrence. The target is not trusted here;
 * the action applier must confirm that the activity exists and is active before mutating a work
 * order.
 */
public record AddActivity(
        String activityCode,
        String ruleType,
        String ruleName
) implements RuleAction {
    public AddActivity {
        Objects.requireNonNull(activityCode);
        Objects.requireNonNull(ruleType);
        Objects.requireNonNull(ruleName);
    }
}
