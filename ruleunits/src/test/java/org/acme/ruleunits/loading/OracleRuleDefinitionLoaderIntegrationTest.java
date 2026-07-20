package org.acme.ruleunits.loading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.acme.ruleunits.oracle.OracleIntegrationTest;
import org.acme.ruleunits.oracle.definition.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(OracleRuleDefinitionLoader.class)
@Sql(scripts = "/sql/oracle-rule-definition-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/oracle-rule-definition-clean.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OracleRuleDefinitionLoaderIntegrationTest extends OracleIntegrationTest {
    private static final LocalDateTime TIME = LocalDateTime.parse("2026-07-18T20:00:00");

    @Autowired RuleSetJpaRepository ruleSets;
    @Autowired RuleTemplateJpaRepository templates;
    @Autowired OracleRuleDefinitionLoader loader;
    @Autowired TestEntityManager entityManager;

    @Test
    void loadsAndDetachesOneCompleteActiveSnapshot() {
        RuleTemplateEntity replacement = templates.save(new RuleTemplateEntity(
                "replace-required", 1, "REPLACE_REQUIRED_ACTIVITIES", "traditional replacement DRL"));
        RuleTemplateEntity refinement = templates.save(new RuleTemplateEntity(
                "refinement", 1, "DEACTIVATE_ALL_AND_ADD", "traditional refinement DRL"));

        RuleSetEntity ruleSet = new RuleSetEntity("ACTIVITY_RULES", 17);
        ruleSet.addStage(stage("RA1", 1, replacement, "RA1-test-1",
                new RuleConditionEntity(1, "REQUIRED_ACTIVITY", "CONTAINS", "L81494", null),
                new RuleActionEntity(1, "REPLACE_ACTIVITY", "L81494", "097079", null)));
        ruleSet.addStage(refinementStage("RA2", 2, refinement, "RA2-test-1", "JM5G513", "FG2802"));
        ruleSet.addStage(refinementStage("RA3", 3, refinement, "RA3-test-1", "CAT3", "AZ9593"));
        ruleSet.markValid(TIME, "valid");
        ruleSet.activate(TIME.plusSeconds(1));
        ruleSets.saveAndFlush(ruleSet);
        entityManager.clear();

        LoadedRuleSetDefinition loaded = loader.loadActive("ACTIVITY_RULES");

        assertThat(loaded.version()).isEqualTo(17);
        assertThat(loaded.stages()).extracting(LoadedRuleStageDefinition::code)
                .containsExactly("RA1", "RA2", "RA3");
        assertThat(loaded.stages().getFirst().rules().getFirst().actions().getFirst().newActivityCode())
                .isEqualTo("097079");
        assertThat(loaded.stages().get(1).rules().getFirst().actions())
                .extracting(LoadedRuleAction::type)
                .containsExactly("DEACTIVATE_ALL", "ADD_ACTIVITY");
    }

    @Test
    void reportsWhenNoActiveRuleSetExists() {
        assertThatThrownBy(() -> loader.loadActive("ACTIVITY_RULES"))
                .isInstanceOf(ActiveRuleSetNotFoundException.class)
                .hasMessageContaining("ACTIVITY_RULES");
    }

    private RuleStageEntity stage(String code, int order, RuleTemplateEntity template,
            String ruleName, RuleConditionEntity condition, RuleActionEntity action) {
        RuleStageEntity stage = new RuleStageEntity(
                code, order, "org.acme.ruleunits.runtime." + code.toLowerCase(), unitName(code));
        RuleDefinitionEntity rule = new RuleDefinitionEntity(template, ruleName, 1, null, null);
        rule.addCondition(condition);
        rule.addAction(action);
        stage.addDefinition(rule);
        return stage;
    }

    private RuleStageEntity refinementStage(String code, int order, RuleTemplateEntity template,
            String ruleName, String conditionValue, String target) {
        String conditionType = code.equals("RA2") ? "JOB_TYPE" : "ACTIVE_CATEGORY";
        String operator = code.equals("RA2") ? "IN" : "CONTAINS";
        RuleStageEntity stage = new RuleStageEntity(
                code, order, "org.acme.ruleunits.runtime." + code.toLowerCase(), unitName(code));
        RuleDefinitionEntity rule = new RuleDefinitionEntity(template, ruleName, 1, null, null);
        rule.addCondition(new RuleConditionEntity(
                1, conditionType, operator, conditionValue, null));
        rule.addAction(new RuleActionEntity(1, "DEACTIVATE_ALL", null, null, null));
        rule.addAction(new RuleActionEntity(2, "ADD_ACTIVITY", null, target, null));
        stage.addDefinition(rule);
        return stage;
    }

    private String unitName(String stage) {
        return stage.substring(0, 1) + stage.substring(1).toLowerCase() + "RuntimeUnit";
    }
}
