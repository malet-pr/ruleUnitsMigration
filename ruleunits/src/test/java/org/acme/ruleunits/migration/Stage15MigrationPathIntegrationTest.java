package org.acme.ruleunits.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import org.acme.ruleunits.config.RuleUnitsRuntimeConfiguration;
import org.acme.ruleunits.loading.OracleRuleDefinitionLoader;
import org.acme.ruleunits.oracle.OracleActivityCatalog;
import org.acme.ruleunits.oracle.OracleIntegrationTest;
import org.acme.ruleunits.oracle.OracleRuleIncidentRecorder;
import org.acme.ruleunits.oracle.OracleWorkOrderRepository;
import org.acme.ruleunits.oracle.definition.RuleActionEntity;
import org.acme.ruleunits.oracle.definition.RuleConditionEntity;
import org.acme.ruleunits.oracle.definition.RuleDefinitionEntity;
import org.acme.ruleunits.oracle.definition.RuleSetEntity;
import org.acme.ruleunits.oracle.definition.RuleSetJpaRepository;
import org.acme.ruleunits.oracle.definition.RuleStageEntity;
import org.acme.ruleunits.oracle.definition.RuleTemplateEntity;
import org.acme.ruleunits.oracle.definition.RuleTemplateJpaRepository;
import org.acme.ruleunits.persistence.RuleProcessingStatus;
import org.acme.ruleunits.persistence.WorkOrderProcessingOutcome;
import org.acme.ruleunits.persistence.WorkOrderRulesService;
import org.acme.ruleunits.refresh.RuleSetRuntimeService;
import org.acme.ruleunits.refresh.RuleSetRefreshStatus;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({
        OracleRuleDefinitionLoader.class,
        OracleWorkOrderRepository.class,
        OracleActivityCatalog.class,
        OracleRuleIncidentRecorder.class,
        RuleUnitsRuntimeConfiguration.class
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Sql(
        scripts = {"/sql/oracle-schema.sql", "/sql/oracle-rule-definition-schema.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
        scripts = {
                "/sql/oracle-clean.sql",
                "/sql/oracle-data.sql",
                "/sql/oracle-stage15-data.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Stage15MigrationPathIntegrationTest extends OracleIntegrationTest {
    private static final String RULE_SET_NAME = "ACTIVITY_RULES";
    private static final LocalDateTime DEFINITION_TIME =
            LocalDateTime.parse("2026-07-18T20:00:00");
    private static final String RULE_TEMPLATE = """
            rule "{{ruleName}}"
            when
            {{when}}
            then
            {{then}}
            end
            """;

    @Autowired RuleSetJpaRepository ruleSets;
    @Autowired RuleTemplateJpaRepository templates;
    @Autowired WorkOrderRulesService service;
    @Autowired RuleSetRuntimeService runtime;
    @Autowired RuleSetSnapshotManager snapshots;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void storeSelectedRules() {
        storeSelectedRuleSet();
    }

    @Test
    void migratesFromOracleDefinitionsThroughRuntimeRulesIntoOracleWorkOrders() {
        assertThatThrownBy(snapshots::acquire)
                .isInstanceOf(RuleExecutionUnavailableException.class);

        WorkOrderProcessingOutcome matching = service.process("matching");
        try (var lease = snapshots.acquire()) {
            assertThat(lease.ruleSetName()).isEqualTo(RULE_SET_NAME);
            assertThat(lease.version()).isEqualTo(17L);
        }

        var refresh = runtime.refresh();
        assertThat(refresh.status()).isEqualTo(RuleSetRefreshStatus.PUBLISHED);
        assertThat(refresh.attemptedVersion()).isEqualTo(17L);
        WorkOrderProcessingOutcome parameterized = service.process("variant");
        WorkOrderProcessingOutcome correctedRa3 = service.process("blocked");

        assertThat(matching.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(matching.lastCompletedStage()).isEqualTo("RA3");
        assertThat(activeCodes("matching")).containsExactly("FG2802");
        assertThat(activityCount("matching", "097079")).isEqualTo(2);
        assertThat(lastRuleCount("matching", "097079", "RA2-test-1")).isEqualTo(2);

        assertThat(parameterized.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(activeCodes("variant")).containsExactlyInAnyOrder("DS7068", "SS8192");
        assertThat(lastAppliedRule("variant", "KO6502")).isEqualTo("RA1-test-1-2");

        assertThat(correctedRa3.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
        assertThat(activeCodes("blocked")).containsExactly("FG2802");
        assertThat(activityCount("blocked", "AZ9593")).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from CT_OT_RULE_INCIDENT", Integer.class)).isZero();

        jdbc.update("update CT_ACTIVIDAD set ACTIVO='N' where CODIGO='FG2802'");
        WorkOrderProcessingOutcome guarded = service.process("guarded");

        assertThat(guarded.status()).isEqualTo(RuleProcessingStatus.BLOCKED);
        assertThat(guarded.lastCompletedStage()).isEqualTo("RA1");
        assertThat(guarded.failedStage()).isEqualTo("RA2");
        assertThat(activeCodes("guarded")).containsExactlyInAnyOrder("E60387", "KO6502");
        assertThat(jdbc.queryForObject(
                "select RULE_NAME from CT_OT_RULE_INCIDENT", String.class))
                .isEqualTo("RA2-test-1");
        assertThat(jdbc.queryForObject(
                "select RULE_ERROR_CODE from CT_OT_RULE_INCIDENT", String.class))
                .isEqualTo("INVALID_ACTIVITY_ADDITION");
        assertThat(jdbc.queryForObject(
                "select RULE_STAGE from CT_OT_RULE_INCIDENT", String.class))
                .isEqualTo("RA2");
    }

    private void storeSelectedRuleSet() {
        RuleTemplateEntity replacementTemplate = templates.save(new RuleTemplateEntity(
                "replace-required", 1, "REPLACE_REQUIRED_ACTIVITIES", RULE_TEMPLATE));
        RuleTemplateEntity categoryTemplate = templates.save(new RuleTemplateEntity(
                "deactivate-category", 1, "DEACTIVATE_CATEGORY", RULE_TEMPLATE));
        RuleTemplateEntity refinementTemplate = templates.save(new RuleTemplateEntity(
                "deactivate-all-add", 1, "DEACTIVATE_ALL_AND_ADD", RULE_TEMPLATE));

        RuleSetEntity ruleSet = new RuleSetEntity(RULE_SET_NAME, 17);
        ruleSet.addStage(ra1(replacementTemplate, categoryTemplate));
        ruleSet.addStage(ra2(refinementTemplate));
        ruleSet.addStage(ra3(refinementTemplate));
        ruleSet.markValid(DEFINITION_TIME, "validated for Stage 15");
        ruleSet.activate(DEFINITION_TIME.plusSeconds(1));
        ruleSets.saveAndFlush(ruleSet);
    }

    private RuleStageEntity ra1(
            RuleTemplateEntity replacementTemplate, RuleTemplateEntity categoryTemplate) {
        RuleStageEntity stage = stage("RA1", 1);
        stage.addDefinition(replacement(
                replacementTemplate, "RA1-test-1", 1,
                List.of("6T8121", "L81494"), "L81494", "097079"));
        stage.addDefinition(replacement(
                replacementTemplate, "RA1-test-1-2", 2,
                List.of("DS7068", "KO6502"), "KO6502", "SS8192"));
        stage.addDefinition(replacement(
                replacementTemplate, "RA1-test-1-3", 3,
                List.of("DS7068", "G99427"), "G99427", "Q79984"));

        RuleDefinitionEntity category = new RuleDefinitionEntity(
                categoryTemplate, "RA1-test-2", 4, null, "FM3X635");
        category.addCondition(new RuleConditionEntity(
                1, "ACTIVE_CATEGORY", "CONTAINS", "CAT2", null));
        category.addAction(new RuleActionEntity(
                1, "DEACTIVATE_CATEGORY", null, null, "CAT2"));
        stage.addDefinition(category);
        return stage;
    }

    private RuleStageEntity ra2(RuleTemplateEntity template) {
        RuleStageEntity stage = stage("RA2", 2);
        RuleDefinitionEntity rule = new RuleDefinitionEntity(
                template, "RA2-test-1", 1, null, null);
        List<String> jobTypes = List.of("KPVG961", "FM3X635", "FH1X042", "JM5G513");
        for (int index = 0; index < jobTypes.size(); index++) {
            rule.addCondition(new RuleConditionEntity(
                    index + 1, "JOB_TYPE", "IN", jobTypes.get(index), null));
        }
        addDeactivateAllAndAdd(rule, "FG2802");
        stage.addDefinition(rule);
        return stage;
    }

    private RuleStageEntity ra3(RuleTemplateEntity template) {
        RuleStageEntity stage = stage("RA3", 3);
        RuleDefinitionEntity rule = new RuleDefinitionEntity(
                template, "RA3-test-1", 1, null, "JM5G513");
        rule.addCondition(new RuleConditionEntity(
                1, "ACTIVE_CATEGORY", "CONTAINS", "CAT3", null));
        addDeactivateAllAndAdd(rule, "AZ9593");
        stage.addDefinition(rule);
        return stage;
    }

    private RuleDefinitionEntity replacement(
            RuleTemplateEntity template,
            String ruleName,
            int order,
            List<String> requiredActivities,
            String oldActivity,
            String newActivity) {
        RuleDefinitionEntity rule = new RuleDefinitionEntity(
                template, ruleName, order, "FINAL", null);
        for (int index = 0; index < requiredActivities.size(); index++) {
            rule.addCondition(new RuleConditionEntity(
                    index + 1,
                    "REQUIRED_ACTIVITY",
                    "CONTAINS",
                    requiredActivities.get(index),
                    null));
        }
        rule.addAction(new RuleActionEntity(
                1, "REPLACE_ACTIVITY", oldActivity, newActivity, null));
        return rule;
    }

    private void addDeactivateAllAndAdd(RuleDefinitionEntity rule, String activityCode) {
        rule.addAction(new RuleActionEntity(
                1, "DEACTIVATE_ALL", null, null, null));
        rule.addAction(new RuleActionEntity(
                2, "ADD_ACTIVITY", null, activityCode, null));
    }

    private RuleStageEntity stage(String code, int order) {
        return new RuleStageEntity(
                code,
                order,
                "org.acme.ruleunits.runtime." + code.toLowerCase(),
                code.substring(0, 1) + code.substring(1).toLowerCase() + "RuntimeUnit");
    }

    private List<String> activeCodes(String workOrderNumber) {
        return jdbc.queryForList("""
                select a.CODIGO
                from CT_OT_ACTIVIDAD ota
                join CT_ACTIVIDAD a on a.ID_ACTIVIDAD=ota.ID_ACTIVIDAD
                join CT_ORDEN_TRABAJO ot
                  on ot.ID_ORDEN_TRABAJO=ota.ID_ORDEN_TRABAJO
                where ot.NRO_OT=? and ota.ACTIVO='S'
                order by ota.ID_OT_ACTIVIDAD
                """, String.class, workOrderNumber);
    }

    private int activityCount(String workOrderNumber, String activityCode) {
        return jdbc.queryForObject("""
                select count(*)
                from CT_OT_ACTIVIDAD ota
                join CT_ACTIVIDAD a on a.ID_ACTIVIDAD=ota.ID_ACTIVIDAD
                join CT_ORDEN_TRABAJO ot
                  on ot.ID_ORDEN_TRABAJO=ota.ID_ORDEN_TRABAJO
                where ot.NRO_OT=? and a.CODIGO=?
                """, Integer.class, workOrderNumber, activityCode);
    }

    private int lastRuleCount(
            String workOrderNumber, String activityCode, String ruleName) {
        return jdbc.queryForObject("""
                select count(*)
                from CT_OT_ACTIVIDAD ota
                join CT_ACTIVIDAD a on a.ID_ACTIVIDAD=ota.ID_ACTIVIDAD
                join CT_REGLA r on r.ID_REGLA=ota.ID_REGLA_APLICADA
                join CT_ORDEN_TRABAJO ot
                  on ot.ID_ORDEN_TRABAJO=ota.ID_ORDEN_TRABAJO
                where ot.NRO_OT=? and a.CODIGO=? and r.NOMBRE=?
                """, Integer.class, workOrderNumber, activityCode, ruleName);
    }

    private String lastAppliedRule(String workOrderNumber, String activityCode) {
        return jdbc.queryForObject("""
                select r.NOMBRE
                from CT_OT_ACTIVIDAD ota
                join CT_ACTIVIDAD a on a.ID_ACTIVIDAD=ota.ID_ACTIVIDAD
                join CT_REGLA r on r.ID_REGLA=ota.ID_REGLA_APLICADA
                join CT_ORDEN_TRABAJO ot
                  on ot.ID_ORDEN_TRABAJO=ota.ID_ORDEN_TRABAJO
                where ot.NRO_OT=? and a.CODIGO=?
                """, String.class, workOrderNumber, activityCode);
    }
}
