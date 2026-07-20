package org.acme.ruleunits.definition;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.acme.ruleunits.domain.WorkOrderType;

/**
 * One parameterized replacement rule assembled from ordered activity conditions and a structured
 * action. It models rules with the same shape while allowing different condition counts and
 * replacement codes.
 */
public record TemplateRuleDefinition(
        String ruleName,
        String ruleType,
        WorkOrderType workOrderType,
        List<RequiredActivityCondition> requiredActivities,
        ReplacementActionDefinition action) {

    public TemplateRuleDefinition {
        Objects.requireNonNull(ruleName);
        Objects.requireNonNull(ruleType);
        Objects.requireNonNull(workOrderType);
        Objects.requireNonNull(action);
        requiredActivities = requiredActivities.stream()
                .sorted(Comparator.comparingInt(RequiredActivityCondition::position))
                .toList();
        if (requiredActivities.isEmpty()) {
            throw new IllegalArgumentException("At least one activity condition is required");
        }
    }
}
