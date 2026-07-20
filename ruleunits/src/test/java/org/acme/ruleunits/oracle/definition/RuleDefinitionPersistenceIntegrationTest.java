package org.acme.ruleunits.oracle.definition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.acme.ruleunits.oracle.OracleIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Sql(scripts = "/sql/oracle-rule-definition-schema.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "/sql/oracle-rule-definition-clean.sql",
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class RuleDefinitionPersistenceIntegrationTest extends OracleIntegrationTest {
    private static final LocalDateTime VALIDATED_AT = LocalDateTime.parse("2026-07-18T20:00:00");

    @Autowired RuleSetJpaRepository ruleSets;
    @Autowired RuleTemplateJpaRepository templates;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestEntityManager entityManager;

    @Test
    void readsOneCompleteActiveVersionInDatabaseOrder() {
        RuleTemplateEntity replacement = templates.save(new RuleTemplateEntity(
                "replace-required", 1, "REPLACE_REQUIRED_ACTIVITIES", "rule template"));
        RuleTemplateEntity accumulate = templates.save(new RuleTemplateEntity(
                "accumulate-one", 1, "ACCUMULATE_LEAVE_ONE", "accumulate template"));

        RuleSetEntity version = new RuleSetEntity("ACTIVITY_RULES", 17);
        RuleStageEntity ra3 = new RuleStageEntity(
                "RA3", 3, "org.acme.ruleunits.runtime.ra3", "Ra3RuntimeUnit");
        RuleStageEntity ra1 = new RuleStageEntity(
                "RA1", 1, "org.acme.ruleunits.runtime.ra1", "Ra1RuntimeUnit");

        RuleDefinitionEntity second = new RuleDefinitionEntity(
                replacement, "RA1-test-1-2", 2, "FINAL", null);
        second.addCondition(new RuleConditionEntity(2, "REQUIRED_ACTIVITY", "CONTAINS", "KO6502", null));
        second.addCondition(new RuleConditionEntity(1, "REQUIRED_ACTIVITY", "CONTAINS", "DS7068", null));
        second.addAction(new RuleActionEntity(1, "REPLACE_ACTIVITY", "KO6502", "SS8192", null));

        RuleDefinitionEntity first = new RuleDefinitionEntity(
                replacement, "RA1-test-1", 1, "FINAL", null);
        first.addCondition(new RuleConditionEntity(1, "REQUIRED_ACTIVITY", "CONTAINS", "L81494", null));
        first.addAction(new RuleActionEntity(1, "REPLACE_ACTIVITY", "L81494", "097079", null));

        RuleDefinitionEntity withAccumulate = new RuleDefinitionEntity(
                accumulate, "withAccumulate", 1, null, null);
        withAccumulate.addCondition(new RuleConditionEntity(
                1, "ACCUMULATED_ACTIVITY", "COUNT_GREATER_THAN", "FG2802", 1L));
        withAccumulate.addAction(new RuleActionEntity(
                1, "DEACTIVATE_EXCEPT_ONE", "FG2802", null, null));

        ra1.addDefinition(second);
        ra1.addDefinition(first);
        ra3.addDefinition(withAccumulate);
        version.addStage(ra3);
        version.addStage(ra1);
        version.markValid(VALIDATED_AT, "valid");
        version.activate(VALIDATED_AT.plusSeconds(1));
        ruleSets.saveAndFlush(version);
        entityManager.clear();

        RuleSetEntity loaded = ruleSets.findByNameAndStatus(
                "ACTIVITY_RULES", RuleSetStatus.ACTIVE).orElseThrow();

        assertThat(loaded.getVersion()).isEqualTo(17);
        assertThat(loaded.getStages()).extracting(RuleStageEntity::getCode)
                .containsExactly("RA1", "RA3");
        assertThat(loaded.getStages().getFirst().getDefinitions())
                .extracting(RuleDefinitionEntity::getName)
                .containsExactly("RA1-test-1", "RA1-test-1-2");
        RuleDefinitionEntity loadedSecond = loaded.getStages().getFirst().getDefinitions().get(1);
        assertThat(loadedSecond.getConditions()).extracting(RuleConditionEntity::getValue)
                .containsExactly("DS7068", "KO6502");
        RuleDefinitionEntity loadedAccumulate = loaded.getStages().get(1).getDefinitions().getFirst();
        assertThat(loadedAccumulate.getTemplate().getShape()).isEqualTo("ACCUMULATE_LEAVE_ONE");
        assertThat(loadedAccumulate.getConditions().getFirst().getNumericValue()).isEqualTo(1);
    }

    @Test
    void allowsMultipleVersionsButOnlyOneActiveVersionPerName() {
        saveActiveVersion(17);
        RuleSetEntity next = new RuleSetEntity("ACTIVITY_RULES", 18);
        next.markValid(VALIDATED_AT.plusSeconds(10), "valid");
        ruleSets.saveAndFlush(next);
        assertThat(ruleSets.findByNameAndVersion("ACTIVITY_RULES", 18)).isPresent();
        next.activate(VALIDATED_AT.plusSeconds(11));

        assertThatThrownBy(() -> ruleSets.saveAndFlush(next))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsActivationWithoutValidationTimestamp() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO CT_RULE_SET
                    (ID_RULE_SET,RULE_SET_NAME,RULE_SET_VERSION,STATUS)
                VALUES (CTS_RULE_SET.NEXTVAL,'ACTIVITY_RULES',17,'ACTIVE')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateConditionPositionsWithinOneRule() {
        jdbc.update("INSERT INTO CT_RULE_SET VALUES (1,'ACTIVITY_RULES',17,'DRAFT',NULL,NULL,NULL)");
        jdbc.update("INSERT INTO CT_RULE_STAGE VALUES (1,1,'RA1',1,'org.acme.ruleunits.runtime.ra1','Ra1RuntimeUnit','S')");
        jdbc.update("INSERT INTO CT_RULE_TEMPLATE VALUES (1,'replace',1,'REPLACE_REQUIRED_ACTIVITIES','template','S')");
        jdbc.update("INSERT INTO CT_RULE_DEFINITION VALUES (1,1,1,'RA1-test-1',1,NULL,NULL,'S')");
        jdbc.update("INSERT INTO CT_RULE_CONDITION VALUES (1,1,1,'REQUIRED_ACTIVITY','CONTAINS','DS7068',NULL)");

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO CT_RULE_CONDITION VALUES (2,1,1,'REQUIRED_ACTIVITY','CONTAINS','KO6502',NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void saveActiveVersion(long version) {
        RuleSetEntity ruleSet = new RuleSetEntity("ACTIVITY_RULES", version);
        ruleSet.markValid(VALIDATED_AT, "valid");
        ruleSet.activate(VALIDATED_AT.plusSeconds(1));
        ruleSets.saveAndFlush(ruleSet);
    }
}
