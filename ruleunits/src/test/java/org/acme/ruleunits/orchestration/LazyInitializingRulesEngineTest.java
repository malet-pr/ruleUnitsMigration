package org.acme.ruleunits.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.acme.ruleunits.domain.WorkOrderEvaluation;
import org.acme.ruleunits.domain.WorkOrderType;
import org.acme.ruleunits.refresh.RuleSetRuntimeService;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.junit.jupiter.api.Test;

class LazyInitializingRulesEngineTest {
    @Test
    void initializesBeforeDelegatingExecution() {
        RuleSetRuntimeService runtime = mock(RuleSetRuntimeService.class);
        WorkOrderRulesEngine delegate = mock(WorkOrderRulesEngine.class);
        WorkOrderEvaluation workOrder = workOrder();
        WorkOrderStageSaver saver = (result, stage) -> {};
        RulesExecutionResult expected = new RulesExecutionResult(
                0, List.of("RA1", "RA2", "RA3"), true);
        when(delegate.execute(workOrder, saver)).thenReturn(expected);
        LazyInitializingRulesEngine engine = new LazyInitializingRulesEngine(runtime, delegate);

        RulesExecutionResult result = engine.execute(workOrder, saver);

        assertThat(result).isSameAs(expected);
        var ordered = inOrder(runtime, delegate);
        ordered.verify(runtime).ensureInitialized();
        ordered.verify(delegate).execute(workOrder, saver);
    }

    @Test
    void unavailableInitializationPreventsExecution() {
        RuleSetRuntimeService runtime = mock(RuleSetRuntimeService.class);
        WorkOrderRulesEngine delegate = mock(WorkOrderRulesEngine.class);
        WorkOrderEvaluation workOrder = workOrder();
        WorkOrderStageSaver saver = (result, stage) -> {};
        org.mockito.Mockito.doThrow(new RuleExecutionUnavailableException())
                .when(runtime).ensureInitialized();
        LazyInitializingRulesEngine engine = new LazyInitializingRulesEngine(runtime, delegate);

        assertThatThrownBy(() -> engine.execute(workOrder, saver))
                .isInstanceOf(RuleExecutionUnavailableException.class);

        verify(delegate, never()).execute(workOrder, saver);
    }

    @Test
    void initializesOnceBeforeOpeningDelegateBatch() {
        RuleSetRuntimeService runtime = mock(RuleSetRuntimeService.class);
        WorkOrderRulesEngine delegate = mock(WorkOrderRulesEngine.class);
        WorkOrderRulesBatch batch = mock(WorkOrderRulesBatch.class);
        when(delegate.openBatch()).thenReturn(batch);
        LazyInitializingRulesEngine engine = new LazyInitializingRulesEngine(runtime, delegate);

        try (WorkOrderRulesBatch opened = engine.openBatch()) {
            assertThat(opened).isSameAs(batch);
        }

        var ordered = inOrder(runtime, delegate);
        ordered.verify(runtime).ensureInitialized();
        ordered.verify(delegate).openBatch();
        verify(batch).close();
    }


    private static WorkOrderEvaluation workOrder() {
        return new WorkOrderEvaluation(
                "WO", "OTHER", WorkOrderType.FINAL, List.of());
    }
}
