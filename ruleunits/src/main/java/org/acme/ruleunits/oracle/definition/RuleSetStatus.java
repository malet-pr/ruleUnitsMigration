package org.acme.ruleunits.oracle.definition;

/**
 * Stored lifecycle of a rule-set version from draft through validation, activation, and
 * retirement. Only an active version is eligible for runtime loading.
 */
public enum RuleSetStatus {
    DRAFT,
    VALID,
    ACTIVE,
    RETIRED
}
