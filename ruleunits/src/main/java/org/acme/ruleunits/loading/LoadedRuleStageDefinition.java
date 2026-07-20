package org.acme.ruleunits.loading;

import java.util.List;

/**
 * Detached configuration for one ordered execution stage and its Rule Unit identity. The selected
 * migration requires exactly RA1, RA2, and RA3 in that order.
 */
public record LoadedRuleStageDefinition(
        String code,
        int order,
        String unitPackage,
        String unitName,
        List<LoadedRuleDefinition> rules) {

    public LoadedRuleStageDefinition {
        rules = List.copyOf(rules);
    }
}
