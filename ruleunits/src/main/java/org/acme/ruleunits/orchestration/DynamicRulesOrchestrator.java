package org.acme.ruleunits.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import org.acme.ruleunits.action.RuleActionApplier;
import org.acme.ruleunits.catalog.ActivityCatalog;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.runtime.ra1.Ra1RuntimeUnit;
import org.acme.ruleunits.runtime.ra2.Ra2RuntimeUnit;
import org.acme.ruleunits.runtime.ra3.Ra3RuntimeUnit;
import org.acme.ruleunits.snapshot.RuleSetLease;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;

/**
 * Executes database-compiled Rule Units in the mandatory RA1 → save → RA2 → save → RA3 → save
 * sequence under one leased snapshot. Each later stage sees the accumulated persisted result,
 * including rule-created occurrences, and RA3 receives active-only category views.
 */
public final class DynamicRulesOrchestrator implements WorkOrderRulesEngine {
    private final RuleActionApplier actionApplier;
    private final RuleSetSnapshotManager snapshots;

    public DynamicRulesOrchestrator(
            ActivityCatalog activityCatalog, RuleSetSnapshotManager snapshots) {
        this.actionApplier = new RuleActionApplier(activityCatalog);
        this.snapshots = Objects.requireNonNull(snapshots);
    }

    @Override
    public RulesExecutionResult execute(
            WorkOrderEvaluation workOrder, WorkOrderStageSaver stageSaver) {
        try (WorkOrderRulesBatch batch = openBatch()) {
            return batch.execute(workOrder, stageSaver);
        }
    }

    @Override
    public WorkOrderRulesBatch openBatch() {
        RuleSetLease lease = snapshots.acquire();
        return new WorkOrderRulesBatch() {
            @Override
            public RulesExecutionResult execute(
                    WorkOrderEvaluation workOrder, WorkOrderStageSaver stageSaver) {
                return executeWithLease(lease, workOrder, stageSaver);
            }

            @Override
            public void close() {
                lease.close();
            }
        };
    }

    private RulesExecutionResult executeWithLease(
            RuleSetLease lease,
            WorkOrderEvaluation workOrder,
            WorkOrderStageSaver stageSaver) {
        Objects.requireNonNull(workOrder);
        Objects.requireNonNull(stageSaver);
        List<String> trace = new ArrayList<>();
        int fired = 0;
        fired += executeStage(
                "RA1", workOrder, stageSaver, () -> executeRa1(lease, workOrder));
        trace.add("RA1");
        fired += executeStage(
                "RA2", workOrder, stageSaver, () -> executeRa2(lease, workOrder));
        trace.add("RA2");
        fired += executeStage(
                "RA3", workOrder, stageSaver, () -> executeRa3(lease, workOrder));
        trace.add("RA3");
        return new RulesExecutionResult(fired, List.copyOf(trace), true);
    }

    private int executeStage(String stage, WorkOrderEvaluation workOrder,
            WorkOrderStageSaver stageSaver, IntSupplier execution) {
        int fired;
        try {
            fired = execution.getAsInt();
        } catch (RuleStageExecutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RuleStageExecutionException(stage, exception);
        }
        stageSaver.save(workOrder, stage);
        return fired;
    }

    private int executeRa1(RuleSetLease lease, WorkOrderEvaluation workOrder) {
        Ra1RuntimeUnit data = new Ra1RuntimeUnit();
        data.getWorkOrders().add(workOrder);
        int fired = fire(lease, "RA1", data);
        actionApplier.apply(workOrder, data.getActions());
        return fired;
    }

    private int executeRa2(RuleSetLease lease, WorkOrderEvaluation workOrder) {
        Ra2RuntimeUnit data = new Ra2RuntimeUnit();
        data.getWorkOrders().add(workOrder);
        int fired = fire(lease, "RA2", data);
        actionApplier.apply(workOrder, data.getActions());
        return fired;
    }

    private int executeRa3(RuleSetLease lease, WorkOrderEvaluation workOrder) {
        Ra3RuntimeUnit data = new Ra3RuntimeUnit();
        data.getWorkOrders().add(workOrder);
        int fired = fire(lease, "RA3", data);
        actionApplier.apply(workOrder, data.getActions());
        return fired;
    }

    private static <T extends RuleUnitData> int fire(
            RuleSetLease lease, String stage, T data) {
        try (RuleUnitInstance<T> instance = lease.createInstance(stage, data)) {
            return instance.fire();
        }
    }
}
