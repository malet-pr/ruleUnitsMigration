package org.acme.ruleunits.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.acme.ruleunits.domain.ActivityOrigin;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.acme.ruleunits.orchestration.RuleStageExecutionException;
import org.acme.ruleunits.orchestration.RulesExecutionResult;
import org.acme.ruleunits.orchestration.WorkOrderRulesBatch;
import org.acme.ruleunits.orchestration.WorkOrderRulesEngine;
import org.acme.ruleunits.orchestration.WorkOrderStageSaver;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class WorkOrderRulesServiceTest {

    @Test
    void logsLegacyCompatibleInitialAndFinalActiveActivityStates() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        WorkOrderRecord workOrder = workOrder(
                "matching", "KPVG961", WorkOrderType.FINAL,
                activity(1, "6T8121", "CAT3"),
                activity(2, "L81494", "CAT2"),
                activity(3, "L81494", "CAT2"),
                activity(4, "ZN8450", null));
        when(repository.findByNumber("matching")).thenReturn(Optional.of(workOrder));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Logger logger = (Logger) LoggerFactory.getLogger(WorkOrderRulesService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service(repository, Set.of("097079", "FG2802")).process("matching");
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains(
                        "### Evaluando OT: matching",
                        "Estado inicial: OT: matching - ACTIVIDADES: "
                                + "[6T8121-1, L81494-1, L81494-1, ZN8450-1]",
                        "Estado final: OT: matching - ACTIVIDADES: [FG2802-1]");
    }

    @Test
    void savesExactMatchingStateAtEveryStageBoundary() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        WorkOrderRecord workOrder = workOrder(
                "matching", "KPVG961", WorkOrderType.FINAL,
                activity(1, "6T8121", "CAT3"),
                activity(2, "L81494", "CAT2"),
                activity(3, "L81494", "CAT2"),
                activity(4, "ZN8450", null));
        workOrder.setExternalReference("unchanged");
        when(repository.findByNumber("matching")).thenReturn(Optional.of(workOrder));
        AtomicInteger saveNumber = new AtomicInteger();
        doAnswer(invocation -> {
            WorkOrderRecord saved = invocation.getArgument(0);
            int current = saveNumber.incrementAndGet();
            if (current == 1) {
                assertThat(saved.getLastCompletedStage()).isEqualTo("RA1");
                assertThat(activeCodes(saved))
                        .containsExactlyInAnyOrder("6T8121", "ZN8450", "097079", "097079");
                assertThat(saved.getActivities())
                        .filteredOn(activity -> activity.getCode().equals("L81494"))
                        .allSatisfy(activity -> {
                            assertThat(activity.isActive()).isFalse();
                            assertThat(activity.getLastAppliedRule()).isEqualTo("RA1-test-1");
                        });
            } else if (current == 2) {
                assertThat(saved.getLastCompletedStage()).isEqualTo("RA2");
                assertThat(activeCodes(saved)).containsExactly("FG2802");
                assertThat(saved.getActivities())
                        .filteredOn(activity -> activity.getCode().equals("097079"))
                        .allSatisfy(activity -> {
                            assertThat(activity.isActive()).isFalse();
                            assertThat(activity.getLastAppliedRule()).isEqualTo("RA2-test-1");
                        });
            } else if (current == 3) {
                assertThat(saved.getRuleProcessingStatus())
                        .isEqualTo(RuleProcessingStatus.COMPLETED);
                assertThat(saved.getLastCompletedStage()).isEqualTo("RA3");
                assertThat(activeCodes(saved)).containsExactly("FG2802");
            }
            assertThat(saved.getExternalReference()).isEqualTo("unchanged");
            return saved;
        }).when(repository).save(any());

        WorkOrderProcessingOutcome outcome = service(
                repository, Set.of("097079", "FG2802")).process("matching");

        assertThat(outcome.found()).isTrue();
        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(saveNumber).hasValue(3);
    }

    @Test
    void savesValidNonmatchingWorkOrderUnchanged() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        ActivityRecord original = activity(1, "I51434", "CAT1");
        WorkOrderRecord workOrder = workOrder(
                "nonmatching", "OTHER", WorkOrderType.ADD, original);
        when(repository.findByNumber("nonmatching")).thenReturn(Optional.of(workOrder));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderProcessingOutcome outcome =
                service(repository, Set.of()).process("nonmatching");

        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(workOrder.getActivities()).containsExactly(original);
        assertThat(original.isActive()).isTrue();
        assertThat(original.getLastAppliedRule()).isNull();
        verify(repository, org.mockito.Mockito.times(3)).save(workOrder);
    }

    @Test
    void ignoresMissingWorkOrder() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        when(repository.findByNumber("missing")).thenReturn(Optional.empty());

        WorkOrderProcessingOutcome outcome =
                service(repository, Set.of()).process("missing");

        assertThat(outcome.found()).isFalse();
        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.NOT_STARTED);
        verify(repository, never()).save(any());
    }

    @Test
    void preservesPreviousStageAndRecordsIssueWhenRa2AdditionIsInvalid() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        ActivityRecord cat3 = activity(1, "E60387", "CAT3");
        WorkOrderRecord workOrder = workOrder(
                "blocked", "JM5G513", WorkOrderType.FINAL,
                cat3, activity(2, "KO6502", null));
        when(repository.findByNumber("blocked")).thenReturn(Optional.of(workOrder));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkOrderProcessingOutcome outcome =
                service(repository, Set.of()).process("blocked");

        assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.BLOCKED);
        assertThat(outcome.lastCompletedStage()).isEqualTo("RA1");
        assertThat(outcome.failedStage()).isEqualTo("RA2");
        assertThat(workOrder.getRuleErrorCode()).isEqualTo("INVALID_ACTIVITY_ADDITION");
        assertThat(workOrder.getRuleErrorDetail()).contains("FG2802");
        assertThat(activeCodes(workOrder)).containsExactly("E60387", "KO6502");
        assertThat(cat3.isActive()).isTrue();
        verify(repository, org.mockito.Mockito.times(1)).save(workOrder);
    }

    @Test
    void batchDeduplicatesBeforeLookupAndUsesOneEngineScopeForFoundWorkOrders() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        WorkOrderRecord first = workOrder(
                "first", "OTHER", WorkOrderType.ADD, activity(1, "I51434", "CAT1"));
        WorkOrderRecord empty = workOrder("empty", "OTHER", WorkOrderType.ADD);
        WorkOrderRecord second = workOrder(
                "second", "OTHER", WorkOrderType.ADD, activity(2, "ZN8450", null));
        when(repository.findByNumber("first")).thenReturn(Optional.of(first));
        when(repository.findByNumber("missing")).thenReturn(Optional.empty());
        when(repository.findByNumber("empty")).thenReturn(Optional.of(empty));
        when(repository.findByNumber("second")).thenReturn(Optional.of(second));

        WorkOrderRulesEngine engine = mock(WorkOrderRulesEngine.class);
        WorkOrderRulesBatch batch = mock(WorkOrderRulesBatch.class);
        when(engine.openBatch()).thenReturn(batch);
        List<String> executed = new ArrayList<>();
        when(batch.execute(any(), any())).thenAnswer(invocation -> {
            WorkOrderEvaluation workOrder = invocation.getArgument(0);
            executed.add(workOrder.getNumber());
            return new RulesExecutionResult(0, List.of(), true);
        });
        WorkOrderRulesService service =
                new WorkOrderRulesService(repository, new WorkOrderMapper(), engine);

        List<WorkOrderProcessingOutcome> outcomes = service.processBatch(
                List.of("first", "missing", "first", "empty", "second", "missing"));

        assertThat(outcomes).hasSize(2);
        assertThat(executed).containsExactly("first", "second");
        verify(repository).findByNumber("first");
        verify(repository).findByNumber("missing");
        verify(repository).findByNumber("empty");
        verify(repository).findByNumber("second");
        verify(engine).openBatch();
        verify(batch, org.mockito.Mockito.times(2)).execute(any(), any());
        verify(batch).close();

    }
    @Test
    void blockedWorkOrderDoesNotStopLaterBatchEntries() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        WorkOrderRecord blocked = workOrder(
                "blocked", "OTHER", WorkOrderType.ADD, activity(1, "I51434", "CAT1"));
        WorkOrderRecord later = workOrder(
                "later", "OTHER", WorkOrderType.ADD, activity(2, "ZN8450", null));
        when(repository.findByNumber("blocked")).thenReturn(Optional.of(blocked));
        when(repository.findByNumber("later")).thenReturn(Optional.of(later));

        WorkOrderRulesEngine engine = mock(WorkOrderRulesEngine.class);
        WorkOrderRulesBatch batch = mock(WorkOrderRulesBatch.class);
        when(engine.openBatch()).thenReturn(batch);
        List<String> executed = new ArrayList<>();
        when(batch.execute(any(), any())).thenAnswer(invocation -> {
            WorkOrderEvaluation workOrder = invocation.getArgument(0);
            WorkOrderStageSaver saver = invocation.getArgument(1);
            executed.add(workOrder.getNumber());
            saver.save(workOrder, "RA1");
            if (workOrder.getNumber().equals("blocked")) {
                throw new RuleStageExecutionException(
                        "RA2", new RuntimeException("simulated rule failure"));
            }
            saver.save(workOrder, "RA2");
            saver.save(workOrder, "RA3");
            return new RulesExecutionResult(0, List.of("RA1", "RA2", "RA3"), true);
        });
        WorkOrderRulesService service =
                new WorkOrderRulesService(repository, new WorkOrderMapper(), engine);

        List<WorkOrderProcessingOutcome> outcomes =
                service.processBatch(List.of("blocked", "later"));

        assertThat(executed).containsExactly("blocked", "later");
        assertThat(outcomes).extracting(WorkOrderProcessingOutcome::status)
                .containsExactly(RuleProcessingStatus.BLOCKED, RuleProcessingStatus.COMPLETED);
        assertThat(outcomes.get(0).lastCompletedStage()).isEqualTo("RA1");
        assertThat(outcomes.get(0).failedStage()).isEqualTo("RA2");
        verify(repository, org.mockito.Mockito.times(4)).save(any());
        verify(batch).close();
    }

    @Test
    void allMissingOrEmptyBatchDoesNotInitializeRuleExecution() {
        WorkOrderRepository repository = mock(WorkOrderRepository.class);
        WorkOrderRecord empty = workOrder("empty", "OTHER", WorkOrderType.ADD);
        when(repository.findByNumber("missing")).thenReturn(Optional.empty());
        when(repository.findByNumber("empty")).thenReturn(Optional.of(empty));
        WorkOrderRulesEngine engine = mock(WorkOrderRulesEngine.class);
        WorkOrderRulesService service =
                new WorkOrderRulesService(repository, new WorkOrderMapper(), engine);

        List<WorkOrderProcessingOutcome> outcomes =
                service.processBatch(List.of("missing", "empty", "missing"));

        assertThat(outcomes).isEmpty();
        verify(engine, never()).openBatch();
    }


    private static WorkOrderRulesService service(
            WorkOrderRepository repository, Set<String> activeCatalog) {
        return new WorkOrderRulesService(
                repository, new WorkOrderMapper(), activeCatalog::contains);
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
