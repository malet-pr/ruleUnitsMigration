package org.acme.ruleunits.refresh;

/**
 * Operational phase taxonomy used to locate a failed refresh without exposing internal rule text
 * or compiler diagnostics.
 */
public enum RuleSetRefreshPhase {
    LOAD,
    VALIDATE,
    ASSEMBLE,
    COMPILE,
    PUBLISH
}
