package org.acme.ruleunits.refresh;

/**
 * Publication outcome of a refresh attempt. A failed result does not imply loss of an already
 * published last-known-good snapshot.
 */
public enum RuleSetRefreshStatus {
    PUBLISHED,
    FAILED
}
