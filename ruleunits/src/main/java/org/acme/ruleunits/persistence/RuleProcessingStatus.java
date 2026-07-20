package org.acme.ruleunits.persistence;

/**
 * Application-level processing state used to distinguish untouched, partially progressed,
 * completed, and blocked work orders without changing the reduced legacy work-order table.
 */
public enum RuleProcessingStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    BLOCKED
}
