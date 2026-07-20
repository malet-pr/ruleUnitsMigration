package org.acme.ruleunits.action;

/**
 * Signals that an action would create an occurrence for an activity that is absent or inactive in
 * the catalog. The failing stage is not applied or saved; orchestration may retain earlier
 * committed stages.
 */
public final class InvalidActivityAdditionException extends RuntimeException {
    private final String activityCode;
    private final String ruleName;

    public InvalidActivityAdditionException(String activityCode, String ruleName) {
        super("Activity to add does not exist or is inactive: " + activityCode);
        this.activityCode = activityCode;
        this.ruleName = ruleName;
    }

    public String getActivityCode() { return activityCode; }
    public String getRuleName() { return ruleName; }
}
