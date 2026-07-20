package org.acme.ruleunits.orchestration;

import org.acme.ruleunits.domain.WorkOrderEvaluation;

/**
 * Closeable execution scope that may hold one immutable snapshot lease across several work orders.
 * Every caller must close it so retired KIE resources can drain after in-flight work completes.
 */
@FunctionalInterface
public interface WorkOrderRulesBatch extends AutoCloseable {
    RulesExecutionResult execute(
            WorkOrderEvaluation workOrder, WorkOrderStageSaver stageSaver);

    @Override
    default void close() {}
}
