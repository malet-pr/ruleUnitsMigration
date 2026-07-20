package org.acme.ruleunits.loading;

/**
 * Detached, positioned condition parameters loaded from Oracle. Ordering is part of the definition
 * contract and is validated before rendering.
 */
public record LoadedRuleCondition(
        int position,
        String type,
        String operator,
        String value,
        Long numericValue) {}
