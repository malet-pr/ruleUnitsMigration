package org.acme.ruleunits.orchestration;

import java.util.List;

/**
 * Framework-neutral summary of a completed pipeline: total fired rules, ordered stage trace, and
 * legacy save eligibility. It is not the HTTP response and carries no mutable work-order state.
 */
public record RulesExecutionResult(
        int firedRules,
        List<String> executionTrace,
        boolean eligibleForSave) {
}
