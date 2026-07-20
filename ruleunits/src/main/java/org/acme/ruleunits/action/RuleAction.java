package org.acme.ruleunits.action;

/**
 * Closed command vocabulary emitted by DRL consequences. Rules describe intended mutations through
 * these values; they do not directly change or persist the work-order aggregate.
 */
public sealed interface RuleAction permits
        ReplaceActivity,
        DeactivateActivityCategory,
        DeactivateAllActivities,
        DeactivateActivitiesExceptOne,
        AddActivity {
}
