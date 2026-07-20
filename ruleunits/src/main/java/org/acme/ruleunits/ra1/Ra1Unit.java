package org.acme.ruleunits.ra1;
import java.util.*;
import org.acme.ruleunits.action.RuleAction;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.drools.ruleunits.api.*;
/**
 * Build-time Rule Unit data contract for the checked-in RA1 reference rules. Consequences append
 * commands rather than mutating the work order directly.
 */
public final class Ra1Unit implements RuleUnitData {
 private final DataStore<WorkOrderEvaluation> workOrders=DataSource.createStore(); private final List<RuleAction> actions=new ArrayList<>();
 public DataStore<WorkOrderEvaluation> getWorkOrders(){return workOrders;} public List<RuleAction> getActions(){return actions;}
}
