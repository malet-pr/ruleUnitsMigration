package org.acme.ruleunits.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.acme.ruleunits.orchestration.RuleGroupExecutorRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WorkOrderRulesControllerConditionTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(
                    RuleGroupExecutorRegistry.class,
                    () -> new RuleGroupExecutorRegistry(Map.of("A", workOrders -> {})))
            .withUserConfiguration(WorkOrderRulesController.class);

    @Test
    void endpointIsDisabledByDefault() {
        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(WorkOrderRulesController.class));
    }

    @Test
    void endpointCanBeExplicitlyEnabled() {
        contextRunner
                .withPropertyValues("rulebridge.rules.execution-endpoint-enabled=true")
                .run(context ->
                        assertThat(context).hasSingleBean(WorkOrderRulesController.class));
    }
}
