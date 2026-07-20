package org.acme.ruleunits.orchestration;

import java.util.Objects;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.refresh.RuleSetRuntimeService;

/**
 * Execution decorator that publishes an initial snapshot on first real use and then delegates
 * unchanged. Concurrent first callers share one initialization attempt; later failures leave
 * execution unavailable or retain an existing snapshot according to runtime state.
 */
public final class LazyInitializingRulesEngine implements WorkOrderRulesEngine {
    private final RuleSetRuntimeService runtime;
    private final WorkOrderRulesEngine delegate;

    public LazyInitializingRulesEngine(
            RuleSetRuntimeService runtime, WorkOrderRulesEngine delegate) {
        this.runtime = Objects.requireNonNull(runtime);
        this.delegate = Objects.requireNonNull(delegate);
    }

    @Override
    public RulesExecutionResult execute(
            WorkOrderEvaluation workOrder, WorkOrderStageSaver stageSaver) {
        runtime.ensureInitialized();
        return delegate.execute(workOrder, stageSaver);
    }

    @Override
    public WorkOrderRulesBatch openBatch() {
        runtime.ensureInitialized();
        return delegate.openBatch();
    }
}
