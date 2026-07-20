package org.acme.ruleunits.compilation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.acme.ruleunits.loading.*;
import org.junit.jupiter.api.Test;

class TraditionalDrlRendererTest {
    private final TraditionalDrlRenderer renderer = new TraditionalDrlRenderer();

    @Test
    void rendersSelectedShapesWithTraditionalPatternsAndStructuredActions() {
        RenderedRuleSet rendered = renderer.render(RuntimeRuleSetFixture.ruleSet());

        assertThat(rendered.stages()).extracting(RenderedRuleStage::stageCode)
                .containsExactly("RA1", "RA2", "RA3");
        assertThat(rendered.stages().getFirst().drl())
                .contains("package org.acme.ruleunits.runtime.ra1;")
                .contains("unit Ra1RuntimeUnit;")
                .contains("activeActivityCodes contains \"6T8121\"")
                .contains("activeActivityCodes contains \"L81494\"")
                .contains("new ReplaceActivity(\"L81494\", \"097079\", \"RA1\", \"RA1-test-1\")")
                .contains("activeActivityCategories contains \"CAT2\"")
                .doesNotContain("/workOrders");
        assertThat(rendered.stages().get(1).drl())
                .contains("jobType in (\"KPVG961\", \"FM3X635\", \"FH1X042\", \"JM5G513\")")
                .contains("new DeactivateAllActivities(\"RA2\", \"RA2-test-1\")")
                .contains("new AddActivity(\"FG2802\", \"RA2\", \"RA2-test-1\")");
        assertThat(rendered.stages().get(2).drl())
                .contains("jobType == \"JM5G513\"")
                .contains("activeActivityCategories contains \"CAT3\"")
                .contains("new AddActivity(\"AZ9593\", \"RA3\", \"RA3-test-1\")")
                .doesNotContain("/activeActivityCodes");
    }

    @Test
    void rendersTraditionalAccumulateSyntaxInTheSeparateCapabilityFixture() {
        RenderedRuleSet rendered = renderer.render(RuntimeRuleSetFixture.accumulateRuleSet());

        assertThat(rendered.stages().get(2).drl())
                .contains("$activities : activeActivityCodes")
                .contains("List($size : size > 1) from accumulate (")
                .contains("String(this == \"FG2802\") from $activities;")
                .contains("collectList()")
                .doesNotContain("/activeActivityCodes");
    }

    @Test
    void rejectsAStoredTemplateMissingARequiredPlaceholder() {
        LoadedRuleSetDefinition original = RuntimeRuleSetFixture.ruleSet();
        LoadedRuleStageDefinition stage = original.stages().getFirst();
        LoadedRuleDefinition rule = stage.rules().getFirst();
        LoadedRuleTemplate invalid = new LoadedRuleTemplate(
                "bad", 1, rule.template().shape(), "rule \"{{ruleName}}\" when {{when}} end", true);
        LoadedRuleDefinition changed = new LoadedRuleDefinition(
                rule.name(), rule.order(), rule.workOrderType(), rule.jobType(), invalid,
                rule.conditions(), rule.actions());
        LoadedRuleStageDefinition changedStage = new LoadedRuleStageDefinition(
                stage.code(), stage.order(), stage.unitPackage(), stage.unitName(),
                List.of(changed, stage.rules().get(1)));
        LoadedRuleSetDefinition changedSet = new LoadedRuleSetDefinition(
                original.name(), original.version(), original.validatedAt(), original.activatedAt(),
                List.of(changedStage, original.stages().get(1), original.stages().get(2)));

        assertThatThrownBy(() -> renderer.render(changedSet))
                .isInstanceOf(DrlRenderingException.class)
                .hasMessageContaining("missing {{then}}");
    }
}
