package org.acme.ruleunits.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.acme.ruleunits.domain.ActivityOccurrence;
import org.acme.ruleunits.domain.ActivityOrigin;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.junit.jupiter.api.Test;

class WorkOrderMapperTest {
    private final WorkOrderMapper mapper = new WorkOrderMapper();

    @Test
    void mapsDuplicatesInactiveStateAndRuleAttributionWithoutDrools() {
        ActivityRecord first = activity(10L, 10L, "L81494", "CAT2", true);
        ActivityRecord second = activity(11L, 11L, "L81494", "CAT2", false);
        second.setLastAppliedRule("earlier");
        WorkOrderRecord record = new WorkOrderRecord(
                "WO", "JT", WorkOrderType.FINAL, List.of(first, second));

        WorkOrderEvaluation domain = mapper.toDomain(record);

        assertThat(domain.getActivities()).hasSize(2);
        assertThat(domain.getActivities())
                .extracting(ActivityOccurrence::getOccurrenceId)
                .containsExactly(10L, 11L);
        assertThat(domain.getActivities().get(1).isActive()).isFalse();
        assertThat(domain.getActivities().get(1).getLastAppliedRule()).isEqualTo("earlier");
    }

    @Test
    void updatesExistingRowsAndAppendsNewRowsWithoutInventingDatabaseIds() {
        ActivityRecord existing = activity(10L, 10L, "L81494", "CAT2", true);
        WorkOrderRecord record = new WorkOrderRecord(
                "WO", "JT", WorkOrderType.FINAL, List.of(existing));
        record.setExternalReference("preserve-me");
        ActivityOccurrence changed = ActivityOccurrence.rehydrate(
                10L, "L81494", "CAT2", 1, ActivityOrigin.ORIGINAL,
                null, false, "RA1-test-1");
        ActivityOccurrence created =
                ActivityOccurrence.ruleCreated(11L, "097079", "CAT2", 1, "RA1");
        WorkOrderEvaluation domain = new WorkOrderEvaluation(
                "WO", "JT", WorkOrderType.FINAL, List.of(changed, created));

        mapper.updateRecord(domain, record);

        assertThat(record.getActivities()).hasSize(2);
        assertThat(existing.getPersistenceId()).isEqualTo(10L);
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getLastAppliedRule()).isEqualTo("RA1-test-1");
        ActivityRecord added = record.getActivities().get(1);
        assertThat(added.getPersistenceId()).isNull();
        assertThat(added.getDomainOccurrenceId()).isEqualTo(11L);
        assertThat(added.getCode()).isEqualTo("097079");
        assertThat(added.getCreatingRuleType()).isEqualTo("RA1");
        assertThat(added.getWorkOrder()).isSameAs(record);
        assertThat(record.getExternalReference()).isEqualTo("preserve-me");
    }

    private static ActivityRecord activity(
            Long persistenceId, long domainId, String code, String category, boolean active) {
        return new ActivityRecord(
                persistenceId, domainId, code, category, 1,
                ActivityOrigin.ORIGINAL, null, active, null);
    }
}
