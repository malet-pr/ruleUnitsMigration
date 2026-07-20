package org.acme.ruleunits.runtime.ra3;

import java.util.ArrayList;
import java.util.List;
import org.acme.ruleunits.action.RuleAction;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.drools.ruleunits.api.*;

/**
 * Stable Rule Unit data identity used only by database-compiled RA3 rules. It receives the
 * accumulated RA2 result and exposes active-only domain views used by corrected category
 * conditions.
 */
public final class Ra3RuntimeUnit implements RuleUnitData {
    private final DataStore<WorkOrderEvaluation> workOrders = DataSource.createStore();
    private final List<RuleAction> actions = new ArrayList<>();
    public DataStore<WorkOrderEvaluation> getWorkOrders() { return workOrders; }
    public List<RuleAction> getActions() { return actions; }
}
