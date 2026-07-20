package org.acme.ruleunits.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntSupplier;
import org.acme.ruleunits.action.RuleActionApplier;
import org.acme.ruleunits.catalog.ActivityCatalog;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.ra1.Ra1Unit;
import org.acme.ruleunits.ra2.Ra2Unit;
import org.acme.ruleunits.ra3.Ra3Unit;
import org.drools.ruleunits.api.RuleUnitData;
import org.drools.ruleunits.api.RuleUnitInstance;
import org.drools.ruleunits.api.RuleUnitProvider;

/**
 * Build-time reference orchestrator for the selected migrated rules. It freezes RA1 → save → RA2 →
 * save → RA3 → save semantics independently of the database-compiled runtime and applies the
 * active-only RA3 category correction.
 */
public final class SelectedRulesOrchestrator {
    private final RuleActionApplier actionApplier;
    private final WorkOrderStageSaver stageSaver;

    public SelectedRulesOrchestrator(ActivityCatalog activityCatalog) {
        this(activityCatalog, (workOrder, stage) -> { });
    }

    public SelectedRulesOrchestrator(
            ActivityCatalog activityCatalog, WorkOrderStageSaver stageSaver) {
        this.actionApplier = new RuleActionApplier(activityCatalog);
        this.stageSaver = Objects.requireNonNull(stageSaver);
    }

    public int executeRa1(WorkOrderEvaluation workOrder) {
        Ra1Unit data = new Ra1Unit();
        data.getWorkOrders().add(workOrder);
        int fired = fire(data);
        actionApplier.apply(workOrder, data.getActions());
        return fired;
    }

    public RulesExecutionResult execute(WorkOrderEvaluation workOrder) {
        List<String> trace = new ArrayList<>();
        int fired = 0;
        fired += executeStage("RA1", workOrder, () -> executeRa1Stage(workOrder));
        trace.add("RA1");
        fired += executeStage("RA2", workOrder, () -> executeRa2Stage(workOrder));
        trace.add("RA2");
        fired += executeStage("RA3", workOrder, () -> executeRa3Stage(workOrder));
        trace.add("RA3");
        return new RulesExecutionResult(fired, List.copyOf(trace), true);
    }

    private int executeStage(
            String stage, WorkOrderEvaluation workOrder, IntSupplier execution) {
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

    private int executeRa1Stage(WorkOrderEvaluation workOrder) {
        Ra1Unit data = new Ra1Unit();
        data.getWorkOrders().add(workOrder);
        int fired = fire(data);
        actionApplier.apply(workOrder, data.getActions());
        return fired;
    }

    private int executeRa2Stage(WorkOrderEvaluation workOrder) {
        Ra2Unit data = new Ra2Unit();
        data.getWorkOrders().add(workOrder);
        int fired = fire(data);
        actionApplier.apply(workOrder, data.getActions());
        return fired;
    }

    private int executeRa3Stage(WorkOrderEvaluation workOrder) {
        Ra3Unit data = new Ra3Unit();
        data.getWorkOrders().add(workOrder);
        int fired = fire(data);
        actionApplier.apply(workOrder, data.getActions());
        return fired;
    }

    private static <T extends RuleUnitData> int fire(T data) {
        try (RuleUnitInstance<T> instance =
                     RuleUnitProvider.get().createRuleUnitInstance(data)) {
            return instance.fire();
        }
    }
}
