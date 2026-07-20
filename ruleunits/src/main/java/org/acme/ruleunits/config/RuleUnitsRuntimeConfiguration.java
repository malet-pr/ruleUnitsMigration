package org.acme.ruleunits.config;

import java.time.Clock;
import java.util.Map;
import org.acme.ruleunits.catalog.ActivityCatalog;
import org.acme.ruleunits.compilation.RuntimeRuleSetCompiler;
import org.acme.ruleunits.compilation.TraditionalDrlRenderer;
import org.acme.ruleunits.loading.RuleSetDefinitionSource;
import org.acme.ruleunits.loading.RuleSetDefinitionValidator;
import org.acme.ruleunits.orchestration.DynamicRulesOrchestrator;
import org.acme.ruleunits.orchestration.LazyInitializingRulesEngine;
import org.acme.ruleunits.orchestration.RuleGroupExecutorRegistry;
import org.acme.ruleunits.orchestration.WorkOrderRulesEngine;
import org.acme.ruleunits.persistence.RuleIncidentRecorder;
import org.acme.ruleunits.persistence.WorkOrderMapper;
import org.acme.ruleunits.persistence.WorkOrderRepository;
import org.acme.ruleunits.persistence.WorkOrderRulesService;
import org.acme.ruleunits.refresh.RuleSetRefreshCoordinator;
import org.acme.ruleunits.refresh.RuleSetRuntimeService;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines the production Spring bean graph from Oracle adapters through validation, compilation,
 * snapshot management, lazy execution, and optional HTTP boundaries. Creating the context does not
 * eagerly load or compile rules.
 */
@Configuration(proxyBeanMethods = false)
public class RuleUnitsRuntimeConfiguration {
    @Bean(destroyMethod = "close")
    RuleSetSnapshotManager ruleSetSnapshotManager() {
        return new RuleSetSnapshotManager();
    }

    @Bean
    TraditionalDrlRenderer traditionalDrlRenderer() {
        return new TraditionalDrlRenderer();
    }

    @Bean
    RuntimeRuleSetCompiler runtimeRuleSetCompiler() {
        return new RuntimeRuleSetCompiler();
    }

    @Bean
    RuleSetDefinitionValidator ruleSetDefinitionValidator(ActivityCatalog activityCatalog) {
        return new RuleSetDefinitionValidator(activityCatalog);
    }

    @Bean
    RuleSetRefreshCoordinator ruleSetRefreshCoordinator(
            @Value("${rulebridge.rules.rule-set-name}") String ruleSetName,
            RuleSetDefinitionSource source,
            RuleSetDefinitionValidator validator,
            TraditionalDrlRenderer renderer,
            RuntimeRuleSetCompiler compiler,
            RuleSetSnapshotManager snapshots) {
        return new RuleSetRefreshCoordinator(
                ruleSetName, source, validator, renderer, compiler, snapshots);
    }

    @Bean
    RuleSetRuntimeService ruleSetRuntimeService(
            RuleSetSnapshotManager snapshots, RuleSetRefreshCoordinator refreshCoordinator) {
        return new RuleSetRuntimeService(snapshots, refreshCoordinator);
    }

    @Bean
    WorkOrderRulesEngine workOrderRulesEngine(
            ActivityCatalog activityCatalog,
            RuleSetSnapshotManager snapshots,
            RuleSetRuntimeService runtime) {
        DynamicRulesOrchestrator dynamic = new DynamicRulesOrchestrator(
                activityCatalog, snapshots);
        return new LazyInitializingRulesEngine(runtime, dynamic);
    }

    @Bean
    WorkOrderMapper workOrderMapper() {
        return new WorkOrderMapper();
    }

    @Bean
    Clock ruleProcessingClock() {
        return Clock.systemUTC();
    }

    @Bean
    WorkOrderRulesService workOrderRulesService(
            WorkOrderRepository repository,
            WorkOrderMapper mapper,
            WorkOrderRulesEngine rulesEngine,
            RuleIncidentRecorder incidentRecorder,
            Clock ruleProcessingClock) {
        return new WorkOrderRulesService(
                repository, mapper, rulesEngine, incidentRecorder, ruleProcessingClock);
    }

    @Bean
    RuleGroupExecutorRegistry ruleGroupExecutorRegistry(
            @Value("${rulebridge.rules.group-code}") String groupCode,
            WorkOrderRulesService workOrderRulesService) {
        return new RuleGroupExecutorRegistry(Map.of(
                groupCode, workOrderNumbers -> workOrderRulesService.processBatch(workOrderNumbers)));
    }
}
