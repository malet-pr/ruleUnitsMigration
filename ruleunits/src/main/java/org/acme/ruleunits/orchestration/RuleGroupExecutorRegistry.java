package org.acme.ruleunits.orchestration;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable registry from externally requested group codes to configured executors. Unknown but
 * syntactically valid groups remain a routing concern, not a rule-engine failure.
 */
public final class RuleGroupExecutorRegistry {
    private final Map<String, RuleGroupExecutor> executors;

    public RuleGroupExecutorRegistry(Map<String, RuleGroupExecutor> executors) {
        this.executors = Map.copyOf(Objects.requireNonNull(executors));
    }

    public Optional<RuleGroupExecutor> find(String groupCode) {
        return Optional.ofNullable(executors.get(groupCode));
    }
}
