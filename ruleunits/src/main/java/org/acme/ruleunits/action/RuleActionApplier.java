package org.acme.ruleunits.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.acme.ruleunits.catalog.ActivityCatalog;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;

/**
 * Applies one stage’s queued commands to a snapshot of that stage input so results do not depend
 * on rule firing order. It preserves duplicate occurrences and validates every effective addition
 * or replacement target against the active activity catalog before performing any stage mutation.
 */
public final class RuleActionApplier {

    private final ActivityCatalog activityCatalog;

    public RuleActionApplier(ActivityCatalog activityCatalog) {
        this.activityCatalog = Objects.requireNonNull(activityCatalog);
    }

    public void apply(WorkOrderEvaluation workOrder, List<? extends RuleAction> actions) {
        List<ActivityOccurrence> stageInput = List.copyOf(workOrder.getActivities());
        validateNewActivities(stageInput, actions);

        long nextId = stageInput.stream()
                .mapToLong(ActivityOccurrence::getOccurrenceId)
                .max()
                .orElse(0L) + 1;
        List<ActivityOccurrence> additions = new ArrayList<>();

        for (RuleAction action : actions) {
            if (action instanceof ReplaceActivity replacement) {
                for (ActivityOccurrence occurrence :
                        activeWithCode(stageInput, replacement.oldCode())) {
                    occurrence.deactivateBy(replacement.ruleName());
                    additions.add(ActivityOccurrence.ruleCreated(
                            nextId++,
                            replacement.newCode(),
                            occurrence.getCategory(),
                            occurrence.getQuantity(),
                            replacement.ruleType()));
                }
            } else if (action instanceof DeactivateActivityCategory deactivation) {
                stageInput.stream()
                        .filter(ActivityOccurrence::isActive)
                        .filter(occurrence ->
                                deactivation.category().equalsIgnoreCase(
                                        occurrence.getCategory()))
                        .forEach(occurrence ->
                                occurrence.deactivateBy(deactivation.ruleName()));
            } else if (action instanceof DeactivateAllActivities deactivation) {
                stageInput.stream()
                        .filter(ActivityOccurrence::isActive)
                        .forEach(occurrence ->
                                occurrence.deactivateBy(deactivation.ruleName()));
            } else if (action instanceof DeactivateActivitiesExceptOne deactivation) {
                List<ActivityOccurrence> matches =
                        activeWithCode(stageInput, deactivation.activityCode());
                matches.stream()
                        .skip(1)
                        .forEach(occurrence ->
                                occurrence.deactivateBy(deactivation.ruleName()));
            } else if (action instanceof AddActivity addition) {
                additions.add(ActivityOccurrence.ruleCreated(
                        nextId++,
                        addition.activityCode(),
                        null,
                        1,
                        addition.ruleType()));
            }
        }

        workOrder.getActivities().addAll(additions);
    }

    private void validateNewActivities(
            List<ActivityOccurrence> stageInput, List<? extends RuleAction> actions) {
        actions.stream()
                .map(action -> newActivity(stageInput, action))
                .filter(Objects::nonNull)
                .distinct()
                .forEach(this::requireActiveActivity);
    }

    private static NewActivity newActivity(
            List<ActivityOccurrence> stageInput, RuleAction action) {
        if (action instanceof AddActivity addition) {
            return new NewActivity(addition.activityCode(), addition.ruleName());
        }
        if (action instanceof ReplaceActivity replacement
                && !activeWithCode(stageInput, replacement.oldCode()).isEmpty()) {
            return new NewActivity(replacement.newCode(), replacement.ruleName());
        }
        return null;
    }

    private void requireActiveActivity(NewActivity activity) {
        if (!activityCatalog.existsAndIsActive(activity.code())) {
            throw new InvalidActivityAdditionException(activity.code(), activity.ruleName());
        }
    }

    private record NewActivity(String code, String ruleName) { }

    private static List<ActivityOccurrence> activeWithCode(
            List<ActivityOccurrence> occurrences, String code) {
        return occurrences.stream()
                .filter(ActivityOccurrence::isActive)
                .filter(occurrence -> occurrence.getCode().equals(code))
                .toList();
    }
}
