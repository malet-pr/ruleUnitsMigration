package org.acme.ruleunits.action;

import java.util.Objects;

/**
 * Queued command that replaces every active occurrence of one code with a distinct rule-created
 * occurrence. Multiple matching occurrences are significant and must produce the same number of
 * replacements.
 */
public record ReplaceActivity(
        String oldCode,
        String newCode,
        String ruleType,
        String ruleName
) implements RuleAction {
    public ReplaceActivity {
        Objects.requireNonNull(oldCode);
        Objects.requireNonNull(newCode);
        Objects.requireNonNull(ruleType);
        Objects.requireNonNull(ruleName);
    }
}
