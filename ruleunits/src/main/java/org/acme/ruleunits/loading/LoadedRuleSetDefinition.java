package org.acme.ruleunits.loading;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Complete immutable rule-set version detached from the JPA persistence context. Validation must
 * accept the entire aggregate before any DRL is assembled or compiled.
 */
public record LoadedRuleSetDefinition(
        String name,
        long version,
        LocalDateTime validatedAt,
        LocalDateTime activatedAt,
        List<LoadedRuleStageDefinition> stages) {

    public LoadedRuleSetDefinition {
        stages = List.copyOf(stages);
    }
}
