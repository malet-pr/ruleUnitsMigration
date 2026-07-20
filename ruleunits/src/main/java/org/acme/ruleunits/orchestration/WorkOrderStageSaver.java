package org.acme.ruleunits.orchestration;

import org.acme.ruleunits.domain.WorkOrderEvaluation;

/**
 * Persistence boundary crossed after each successfully applied RA stage and before the next stage
 * starts. A failing stage never invokes this boundary, so earlier committed stages remain the
 * observable work-order state.
 */
@FunctionalInterface
public interface WorkOrderStageSaver {

    void save(WorkOrderEvaluation workOrder, String completedStage);
}
