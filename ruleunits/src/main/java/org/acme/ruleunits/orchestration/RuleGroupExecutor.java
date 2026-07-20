package org.acme.ruleunits.orchestration;

import java.util.List;

/**
 * Application boundary for running a requested group against an ordered work-order-number batch.
 * The HTTP layer depends on this contract rather than assuming every deployment uses group A.
 */
@FunctionalInterface
public interface RuleGroupExecutor {
    void execute(List<String> workOrderNumbers);
}
