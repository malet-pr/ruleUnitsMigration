package org.acme.ruleunits.persistence;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.WorkOrderEvaluation;

/**
 * Maps between persistence-neutral records and the rule-domain aggregate while preserving row
 * identity, duplicate occurrences, origin, and rule attribution. Mapping itself performs no
 * repository access or rule execution.
 */
public final class WorkOrderMapper {

    public WorkOrderEvaluation toDomain(WorkOrderRecord source) {
        List<ActivityOccurrence> activities = source.getActivities().stream()
                .map(this::toDomain)
                .toList();
        return new WorkOrderEvaluation(
                source.getNumber(), source.getJobType(), source.getWorkOrderType(), activities);
    }

    public void updateRecord(WorkOrderEvaluation source, WorkOrderRecord target) {
        Map<Long, ActivityRecord> existing = target.getActivities().stream()
                .collect(Collectors.toMap(
                        ActivityRecord::getDomainOccurrenceId,
                        Function.identity()));

        for (ActivityOccurrence occurrence : source.getActivities()) {
            ActivityRecord record = existing.get(occurrence.getOccurrenceId());
            if (record == null) {
                record = new ActivityRecord(
                        null,
                        occurrence.getOccurrenceId(),
                        occurrence.getCode(),
                        occurrence.getCategory(),
                        occurrence.getQuantity(),
                        occurrence.getOrigin(),
                        occurrence.getCreatingRuleType(),
                        occurrence.isActive(),
                        occurrence.getLastAppliedRule());
                target.addActivity(record);
                existing.put(occurrence.getOccurrenceId(), record);
            } else {
                updateActivity(occurrence, record);
            }
        }
    }

    private ActivityOccurrence toDomain(ActivityRecord source) {
        return ActivityOccurrence.rehydrate(
                source.getDomainOccurrenceId(),
                source.getCode(),
                source.getCategory(),
                source.getQuantity(),
                source.getOrigin(),
                source.getCreatingRuleType(),
                source.isActive(),
                source.getLastAppliedRule());
    }

    private void updateActivity(ActivityOccurrence source, ActivityRecord target) {
        target.setCode(source.getCode());
        target.setCategory(source.getCategory());
        target.setQuantity(source.getQuantity());
        target.setOrigin(source.getOrigin());
        target.setCreatingRuleType(source.getCreatingRuleType());
        target.setActive(source.isActive());
        target.setLastAppliedRule(source.getLastAppliedRule());
    }
}
