package org.acme.ruleunits.ra3;

import java.util.ArrayList;
import java.util.List;
import org.acme.ruleunits.action.RuleAction;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.drools.ruleunits.api.DataSource;
import org.drools.ruleunits.api.DataStore;
import org.drools.ruleunits.api.RuleUnitData;

/**
 * Build-time Rule Unit data contract for final RA3 refinement. Category conditions intentionally
 * inspect active occurrences only, correcting the approved legacy defect where inactive originals
 * could trigger RA3.
 */
public final class Ra3Unit implements RuleUnitData {

    private final DataStore<WorkOrderEvaluation> workOrders = DataSource.createStore();
    private final List<RuleAction> actions = new ArrayList<>();

    public DataStore<WorkOrderEvaluation> getWorkOrders() {
        return workOrders;
    }

    public List<RuleAction> getActions() {
        return actions;
    }
}
