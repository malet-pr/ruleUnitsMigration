package org.acme.ruleunits.compilation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.acme.ruleunits.action.*;
import org.acme.ruleunits.domain.*;
import org.acme.ruleunits.runtime.ra1.Ra1RuntimeUnit;
import org.acme.ruleunits.runtime.ra2.Ra2RuntimeUnit;
import org.acme.ruleunits.runtime.ra3.Ra3RuntimeUnit;
import org.junit.jupiter.api.Test;
import org.kie.api.KieServices;

class RuntimeRuleSetCompilerTest {
    @Test
    void rejectsInvalidRenderedDrlWithCompilerDiagnostics() {
        assertThatThrownBy(() -> new RuntimeRuleSetCompiler()
                .compile(RuntimeRuleSetFixture.malformedRenderedRuleSet()))
                .isInstanceOf(RuleSetCompilationException.class)
                .hasMessageContaining("Runtime rule-set compilation failed")
                .hasMessageNotContaining("/home/");
    }

    @Test
    void compilesDiscoversInstantiatesAndExecutesEveryRuntimeUnit() {
        try (CompiledRuleSet compiled = new RuntimeRuleSetCompiler()
                .compile(RuntimeRuleSetFixture.renderedRuleSet())) {
            assertThat(compiled.name()).isEqualTo("ACTIVITY_RULES");
            assertThat(compiled.version()).isEqualTo(17);
            assertThat(compiled.stages()).containsExactlyInAnyOrder("RA1", "RA2", "RA3");

            Ra1RuntimeUnit ra1 = new Ra1RuntimeUnit();
            ra1.getWorkOrders().add(workOrder("FM3X635",
                    ActivityOccurrence.original(1, "6T8121", null, 1),
                    ActivityOccurrence.original(2, "L81494", "CAT2", 1)));
            try (var instance = compiled.createInstance("RA1", ra1)) {
                assertThat(instance.fire()).isEqualTo(2);
            }
            assertThat(ra1.getActions()).extracting(action -> action.getClass().getSimpleName())
                    .containsExactlyInAnyOrder("ReplaceActivity", "DeactivateActivityCategory");

            Ra2RuntimeUnit ra2 = new Ra2RuntimeUnit();
            ra2.getWorkOrders().add(workOrder("JM5G513",
                    ActivityOccurrence.original(1, "ORIGINAL", null, 1)));
            try (var instance = compiled.createInstance("RA2", ra2)) {
                assertThat(instance.fire()).isEqualTo(1);
            }
            assertThat(ra2.getActions()).containsExactly(
                    new DeactivateAllActivities("RA2", "RA2-test-1"),
                    new AddActivity("FG2802", "RA2", "RA2-test-1"));

            Ra3RuntimeUnit ra3 = new Ra3RuntimeUnit();
            ra3.getWorkOrders().add(workOrder("JM5G513",
                    ActivityOccurrence.original(1, "ORIGINAL", "CAT3", 1)));
            try (var instance = compiled.createInstance("RA3", ra3)) {
                assertThat(instance.fire()).isEqualTo(1);
            }
            assertThat(ra3.getActions()).containsExactly(
                    new DeactivateAllActivities("RA3", "RA3-test-1"),
                    new AddActivity("AZ9593", "RA3", "RA3-test-1"));
        }
    }

    @Test
    void compilesAndExecutesTraditionalAccumulateSyntax() {
        try (CompiledRuleSet compiled = new RuntimeRuleSetCompiler()
                .compile(RuntimeRuleSetFixture.accumulateRenderedRuleSet())) {
            Ra3RuntimeUnit ra3 = new Ra3RuntimeUnit();
            ra3.getWorkOrders().add(workOrder("OTHER",
                    ActivityOccurrence.ruleCreated(1, "FG2802", null, 1, "RA2"),
                    ActivityOccurrence.ruleCreated(2, "FG2802", null, 1, "RA2")));

            try (var instance = compiled.createInstance("RA3", ra3)) {
                assertThat(instance.fire()).isEqualTo(1);
            }
            assertThat(ra3.getActions()).containsExactly(
                    new DeactivateActivitiesExceptOne("FG2802", "RA3", "withAccumulate"));
        }
    }

    @Test
    void closeRemovesTheKieModuleAndRejectsNewInstances() {
        CompiledRuleSet compiled = new RuntimeRuleSetCompiler()
                .compile(RuntimeRuleSetFixture.renderedRuleSet());
        var releaseId = compiled.releaseId();
        assertThat(KieServices.get().getRepository().getKieModule(releaseId)).isNotNull();

        compiled.close();
        compiled.close();

        assertThat(KieServices.get().getRepository().getKieModule(releaseId)).isNull();
        assertThatThrownBy(() -> compiled.createInstance("RA1", new Ra1RuntimeUnit()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void successiveCompilationsAreIsolatedAndRemainExecutable() {
        RuntimeRuleSetCompiler compiler = new RuntimeRuleSetCompiler();
        try (CompiledRuleSet first = compiler.compile(RuntimeRuleSetFixture.renderedRuleSet());
                CompiledRuleSet second = compiler.compile(RuntimeRuleSetFixture.renderedRuleSet())) {
            assertThat(first.releaseId()).isNotEqualTo(second.releaseId());
            first.close();

            Ra1RuntimeUnit data = new Ra1RuntimeUnit();
            data.getWorkOrders().add(workOrder("FM3X635",
                    ActivityOccurrence.original(1, "6T8121", null, 1),
                    ActivityOccurrence.original(2, "L81494", "CAT2", 1)));
            try (var instance = second.createInstance("RA1", data)) {
                assertThat(instance.fire()).isEqualTo(2);
            }
        }
    }

    private WorkOrderEvaluation workOrder(String jobType, ActivityOccurrence... activities) {
        return new WorkOrderEvaluation("WO", jobType, WorkOrderType.FINAL, List.of(activities));
    }
}
