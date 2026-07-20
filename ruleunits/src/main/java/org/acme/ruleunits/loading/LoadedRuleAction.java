package org.acme.ruleunits.loading;

/**
 * Detached, positioned action parameters loaded from Oracle. The record is immutable compilation
 * input and performs no work-order mutation.
 */
public record LoadedRuleAction(
        int position,
        String type,
        String oldActivityCode,
        String newActivityCode,
        String category) {}
