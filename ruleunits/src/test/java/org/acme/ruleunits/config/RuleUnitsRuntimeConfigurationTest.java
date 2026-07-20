package org.acme.ruleunits.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.acme.ruleunits.catalog.ActivityCatalog;
import org.acme.ruleunits.compilation.CompiledRuleSet;
import org.acme.ruleunits.loading.RuleSetDefinitionSource;
import org.acme.ruleunits.orchestration.RuleGroupExecutorRegistry;
import org.acme.ruleunits.orchestration.LazyInitializingRulesEngine;
import org.acme.ruleunits.orchestration.WorkOrderRulesEngine;
import org.acme.ruleunits.persistence.RuleIncidentRecorder;
import org.acme.ruleunits.persistence.WorkOrderRepository;
import org.acme.ruleunits.persistence.WorkOrderRulesService;
import org.acme.ruleunits.refresh.RuleSetRuntimeService;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RuleUnitsRuntimeConfigurationTest {
    @Test
    void registersLazyProductionRuntimeWithoutLoadingRulesAtContextStartup() {
        RuleSetDefinitionSource source = mock(RuleSetDefinitionSource.class);
        CompiledRuleSet compiled = mock(CompiledRuleSet.class);
        when(compiled.name()).thenReturn("ACTIVITY_RULES");
        when(compiled.version()).thenReturn(17L);
        when(compiled.stages()).thenReturn(Set.of("RA1", "RA2", "RA3"));
        AtomicReference<RuleSetSnapshotManager> manager = new AtomicReference<>();

        new ApplicationContextRunner()
                .withPropertyValues("rulebridge.rules.group-code=A")
                .withUserConfiguration(RuleUnitsRuntimeConfiguration.class)
                .withPropertyValues("rulebridge.rules.rule-set-name=ACTIVITY_RULES")
                .withBean(RuleSetDefinitionSource.class, () -> source)
                .withBean(ActivityCatalog.class, () -> code -> true)
                .withBean(WorkOrderRepository.class, () -> mock(WorkOrderRepository.class))
                .withBean(RuleIncidentRecorder.class, RuleIncidentRecorder::noOp)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RuleSetSnapshotManager.class);
                    assertThat(context).hasSingleBean(RuleSetRuntimeService.class);
                    assertThat(context).hasSingleBean(WorkOrderRulesEngine.class);
                    assertThat(context).hasSingleBean(WorkOrderRulesService.class);
                    assertThat(context.getBean(WorkOrderRulesEngine.class))
                            .isInstanceOf(LazyInitializingRulesEngine.class);
                    assertThat(context).hasSingleBean(RuleGroupExecutorRegistry.class);
                    verifyNoInteractions(source);

                    manager.set(context.getBean(RuleSetSnapshotManager.class));
                    manager.get().publish(compiled);
                });

        assertThat(manager).hasValueSatisfying(snapshot ->
                assertThat(snapshot.isAvailable()).isFalse());
        verify(compiled).close();
        verifyNoInteractions(source);
    }
}
