package org.acme.ruleunits.orchestration;

import org.acme.ruleunits.domain.WorkOrderEvaluation;

/**
 * Application port for staged work-order rule execution. Implementations own rule runtime concerns
 * but delegate every successful stage persistence boundary to the supplied saver.
 */
@FunctionalInterface
public interface WorkOrderRulesEngine {
    RulesExecutionResult execute(
            WorkOrderEvaluation workOrder, WorkOrderStageSaver stageSaver);

    default WorkOrderRulesBatch openBatch() {
        return this::execute;
    }
}
