package org.acme.ruleunits.runtime.ra2;

import java.util.ArrayList;
import java.util.List;
import org.acme.ruleunits.action.RuleAction;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.drools.ruleunits.api.*;

/**
 * Stable Rule Unit data identity used only by database-compiled RA2 rules. It carries the
 * accumulated RA1 work order and a queue of refinement commands.
 */
public final class Ra2RuntimeUnit implements RuleUnitData {
    private final DataStore<WorkOrderEvaluation> workOrders = DataSource.createStore();
    private final List<RuleAction> actions = new ArrayList<>();
    public DataStore<WorkOrderEvaluation> getWorkOrders() { return workOrders; }
    public List<RuleAction> getActions() { return actions; }
}
