package org.acme.ruleunits.runtime.ra1;

import java.util.ArrayList;
import java.util.List;
import org.acme.ruleunits.action.RuleAction;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.drools.ruleunits.api.*;

/**
 * Stable Rule Unit data identity used only by database-compiled RA1 rules. Its package is
 * intentionally separate from build-time units to prevent parent-first classloading collisions
 * across compilation modes.
 */
public final class Ra1RuntimeUnit implements RuleUnitData {
    private final DataStore<WorkOrderEvaluation> workOrders = DataSource.createStore();
    private final List<RuleAction> actions = new ArrayList<>();
    public DataStore<WorkOrderEvaluation> getWorkOrders() { return workOrders; }
    public List<RuleAction> getActions() { return actions; }
}
