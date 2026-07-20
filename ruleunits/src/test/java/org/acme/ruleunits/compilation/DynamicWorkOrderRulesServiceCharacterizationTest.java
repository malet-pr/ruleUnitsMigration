package org.acme.ruleunits.compilation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.acme.ruleunits.domain.ActivityOrigin;
import org.acme.ruleunits.domain.WorkOrderType;
import org.acme.ruleunits.orchestration.DynamicRulesOrchestrator;
import org.acme.ruleunits.persistence.ActivityRecord;
import org.acme.ruleunits.persistence.RuleProcessingStatus;
import org.acme.ruleunits.persistence.WorkOrderMapper;
import org.acme.ruleunits.persistence.WorkOrderProcessingOutcome;
import org.acme.ruleunits.persistence.WorkOrderRecord;
import org.acme.ruleunits.persistence.WorkOrderRepository;
import org.acme.ruleunits.persistence.WorkOrderRulesService;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DynamicWorkOrderRulesServiceCharacterizationTest {
    private static RuleSetSnapshotManager snapshots;

    @BeforeAll
    static void publishSelectedRuntimeRules() {
        snapshots = new RuleSetSnapshotManager();
        snapshots.publish(new RuntimeRuleSetCompiler()
                .compile(RuntimeRuleSetFixture.renderedRuleSet()));
    }

    @AfterAll
    static void closeRuntimeRules() {
        snapshots.close();
    }

    @Test
    void duplicateOriginalOccurrencesEachProduceAReplacementAndKeepRuleAttribution() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        WorkOrderRecord workOrder = workOrder(
                "duplicates", "KPVG961", WorkOrderType.FINAL,
                activity(1, "6T8121", "CAT3"),
                activity(2, "L81494", "CAT2"),
                activity(3, "L81494", "CAT2"),
                activity(4, "ZN8450", null));
        when(repository.findByNumber("duplicates")).thenReturn(Optional.of(workOrder));
        AtomicInteger saves = new AtomicInteger();
        doAnswer(invocation -> {
            WorkOrderRecord saved = invocation.getArgument(0);
            if (saves.incrementAndGet() == 1) {
                assertThat(activeCodes(saved))
                        .containsExactlyInAnyOrder("6T8121", "ZN8450", "097079", "097079");
                assertThat(saved.getActivities())
                        .filteredOn(activity -> activity.getCode().equals("L81494"))
                        .allSatisfy(activity -> {
                            assertThat(activity.isActive()).isFalse();
                            assertThat(activity.getLastAppliedRule()).isEqualTo("RA1-test-1");
                        });
            }
            return saved;
        }).when(repository).save(any());

        WorkOrderProcessingOutcome outcome = service(
                repository, Set.of("097079", "FG2802")).process("duplicates");

        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(saves).hasValue(3);
        assertThat(activeCodes(workOrder)).containsExactly("FG2802");
        assertThat(workOrder.getActivities())
                .filteredOn(activity -> activity.getCode().equals("097079"))
                .hasSize(2)
                .allSatisfy(activity -> {
                    assertThat(activity.isActive()).isFalse();
                    assertThat(activity.getLastAppliedRule()).isEqualTo("RA2-test-1");
                });
    }

    @Test
    void categoryDeactivationAndLaterRefinementPreserveTheRuleThatChangedEachOccurrence() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        ActivityRecord cat2 = activity(1, "G93210", "CAT2");
        ActivityRecord cat1 = activity(2, "ML6351", "CAT1");
        WorkOrderRecord workOrder = workOrder(
                "category", "FM3X635", WorkOrderType.FINAL,
                cat2, cat1, activity(3, "097079", null));
        when(repository.findByNumber("category")).thenReturn(Optional.of(workOrder));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderProcessingOutcome outcome = service(
                repository, Set.of("FG2802")).process("category");

        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(activeCodes(workOrder)).containsExactly("FG2802");
        assertThat(cat2.getLastAppliedRule()).isEqualTo("RA1-test-2");
        assertThat(cat1.getLastAppliedRule()).isEqualTo("RA2-test-1");
        verify(repository, times(3)).save(workOrder);
    }

    @Test
    void correctedRa3RequiresAnActiveCategoryAfterRa2() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        ActivityRecord cat3 = activity(1, "E60387", "CAT3");
        WorkOrderRecord workOrder = workOrder(
                "corrected-ra3", "JM5G513", WorkOrderType.FINAL,
                cat3, activity(2, "KO6502", null));
        when(repository.findByNumber("corrected-ra3")).thenReturn(Optional.of(workOrder));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderProcessingOutcome outcome = service(
                repository, Set.of("FG2802", "AZ9593")).process("corrected-ra3");

        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(activeCodes(workOrder)).containsExactly("FG2802");
        assertThat(cat3.getLastAppliedRule()).isEqualTo("RA2-test-1");
        assertThat(workOrder.getActivities())
                .noneMatch(activity -> activity.getCode().equals("AZ9593"));
        verify(repository, times(3)).save(workOrder);
    }

    @Test
    void nonmatchingValidWorkOrderStillCrossesEverySaveBoundary() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        ActivityRecord original = activity(1, "I51434", "CAT1");
        WorkOrderRecord workOrder = workOrder(
                "nonmatching", "OTHER", WorkOrderType.ADD, original);
        when(repository.findByNumber("nonmatching")).thenReturn(Optional.of(workOrder));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderProcessingOutcome outcome = service(repository, Set.of()).process("nonmatching");

        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(workOrder.getActivities()).containsExactly(original);
        assertThat(original.isActive()).isTrue();
        assertThat(original.getLastAppliedRule()).isNull();
        verify(repository, times(3)).save(workOrder);
    }

    @Test
    void missingRequestedWorkOrderIsIgnored() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        when(repository.findByNumber("missing")).thenReturn(Optional.empty());

        WorkOrderProcessingOutcome outcome = service(repository, Set.of()).process("missing");

        assertThat(outcome.found()).isFalse();
        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.NOT_STARTED);
        verify(repository, never()).save(any());
    }

    @Test
    void invalidRa2AdditionLeavesTheWorkOrderAtTheSavedRa1State() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        ActivityRecord cat3 = activity(1, "E60387", "CAT3");
        ActivityRecord other = activity(2, "KO6502", null);
        WorkOrderRecord workOrder = workOrder(
                "blocked", "JM5G513", WorkOrderType.FINAL, cat3, other);
        when(repository.findByNumber("blocked")).thenReturn(Optional.of(workOrder));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderProcessingOutcome outcome = service(repository, Set.of()).process("blocked");

        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.BLOCKED);
        assertThat(outcome.lastCompletedStage()).isEqualTo("RA1");
        assertThat(outcome.failedStage()).isEqualTo("RA2");
        assertThat(workOrder.getRuleErrorCode()).isEqualTo("INVALID_ACTIVITY_ADDITION");
        assertThat(workOrder.getRuleErrorDetail()).contains("FG2802");
        assertThat(activeCodes(workOrder)).containsExactly("E60387", "KO6502");
        assertThat(cat3.isActive()).isTrue();
        assertThat(other.isActive()).isTrue();
        verify(repository, times(1)).save(workOrder);
    }

    private static WorkOrderRulesService service(
            WorkOrderRepository repository, Set<String> activeCatalog) {
        DynamicRulesOrchestrator engine = new DynamicRulesOrchestrator(
                activeCatalog::contains, snapshots);
        return new WorkOrderRulesService(repository, new WorkOrderMapper(), engine);
    }

    private static WorkOrderRecord workOrder(
            String number, String jobType, WorkOrderType type, ActivityRecord... activities) {
        return new WorkOrderRecord(number, jobType, type, List.of(activities));
    }

    private static ActivityRecord activity(long id, String code, String category) {
        return new ActivityRecord(
                id, id, code, category, 1,
                ActivityOrigin.ORIGINAL, null, true, null);
    }

    private static List<String> activeCodes(WorkOrderRecord workOrder) {
        return workOrder.getActivities().stream()
                .filter(ActivityRecord::isActive)
                .map(ActivityRecord::getCode)
                .toList();
    }
}
