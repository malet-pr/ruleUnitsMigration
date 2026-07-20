package org.acme.ruleunits.loading;

import java.util.List;

/**
 * Detached representation of one stored rule, including its template, ordered conditions, and
 * ordered actions. It contains configuration only and is not a Drools fact.
 */
public record LoadedRuleDefinition(
        String name,
        int order,
        String workOrderType,
        String jobType,
        LoadedRuleTemplate template,
        List<LoadedRuleCondition> conditions,
        List<LoadedRuleAction> actions) {

    public LoadedRuleDefinition {
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
    }
}
