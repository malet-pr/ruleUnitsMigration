package org.acme.ruleunits.persistence;

import java.util.Optional;

/**
 * Persistence port for loading by work-order number and saving an exact completed-stage state.
 * Valid nonmatching work orders still cross this save boundary; missing work orders are ignored
 * before execution.
 */
public interface WorkOrderRepository {
    Optional<WorkOrderRecord> findByNumber(String workOrderNumber);
    WorkOrderRecord save(WorkOrderRecord workOrder);
}
