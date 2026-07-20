package org.acme.ruleunits.persistence;

/**
 * Internal result of processing one requested work order, including missing and blocked cases. The
 * legacy-compatible endpoint intentionally reduces batch success to a Boolean rather than exposing
 * this detail.
 */
public record WorkOrderProcessingOutcome(
        boolean found,
        RuleProcessingStatus status,
        String lastCompletedStage,
        String failedStage) {

    public static WorkOrderProcessingOutcome missing() {
        return new WorkOrderProcessingOutcome(
                false, RuleProcessingStatus.NOT_STARTED, null, null);
    }
}
