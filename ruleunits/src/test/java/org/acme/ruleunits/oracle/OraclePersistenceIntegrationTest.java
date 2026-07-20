package org.acme.ruleunits.oracle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.*;
import org.acme.ruleunits.persistence.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.*;

@DataJpaTest
@Import({OracleWorkOrderRepository.class,OracleActivityCatalog.class,OracleRuleIncidentRecorder.class})
@Transactional(propagation=Propagation.NOT_SUPPORTED)
@Sql(scripts="/sql/oracle-schema.sql",executionPhase=Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts={"/sql/oracle-clean.sql","/sql/oracle-data.sql"},executionPhase=Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class OraclePersistenceIntegrationTest extends OracleIntegrationTest {
 @Autowired OracleWorkOrderRepository workOrders;
 @Autowired OracleActivityCatalog catalog;
 @Autowired OracleRuleIncidentRecorder incidents;
 @Autowired JdbcTemplate jdbc;

 @Test
 void persistsMatchingStagesWithGeneratedIdsAndAttribution(){
  var outcome=service().process("matching");
  assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
  assertThat(activeCodes("matching")).containsExactly("FG2802");
  assertThat(jdbc.queryForObject("select count(*) from CT_OT_ACTIVIDAD where ID_ORDEN_TRABAJO=10 and ID_REGLA_TIPO=1",Integer.class)).isEqualTo(2);
  assertThat(jdbc.queryForObject("select count(*) from CT_OT_ACTIVIDAD where ID_ORDEN_TRABAJO=10 and ID_REGLA_APLICADA=3",Integer.class)).isEqualTo(4);
 }

 @Test
 void readsAndCompletesValidNonmatchingWorkOrder(){
  var outcome=service().process("nonmatching");
  assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.COMPLETED);
  assertThat(activeCodes("nonmatching")).containsExactly("I51434");
 }

 @Test
 void ignoresMissingWorkOrder(){
  assertThat(service().process("missing").found()).isFalse();
  assertThat(jdbc.queryForObject("select count(*) from CT_OT_RULE_INCIDENT",Integer.class)).isZero();
 }

 @Test
 void commitsEarlierStageAndRecordsIncidentWhenRa2AdditionIsInactive(){
  jdbc.update("update CT_ACTIVIDAD set ACTIVO='N' where CODIGO='FG2802'");
  var outcome=service().process("blocked");
  assertThat(outcome.status()).isEqualTo(RuleProcessingStatus.BLOCKED);
  assertThat(outcome.lastCompletedStage()).isEqualTo("RA1");
  assertThat(outcome.failedStage()).isEqualTo("RA2");
  assertThat(activeCodes("blocked")).containsExactlyInAnyOrder("E60387","KO6502");
  assertThat(jdbc.queryForObject("select RULE_NAME from CT_OT_RULE_INCIDENT",String.class)).isEqualTo("RA2-test-1");
  assertThat(jdbc.queryForObject("select RULE_ERROR_CODE from CT_OT_RULE_INCIDENT",String.class)).isEqualTo("INVALID_ACTIVITY_ADDITION");
  assertThat(jdbc.queryForObject("select STATUS from CT_OT_RULE_INCIDENT",String.class)).isEqualTo("OPEN");
 }

 private WorkOrderRulesService service(){
  return new WorkOrderRulesService(workOrders,new WorkOrderMapper(),catalog,incidents,
    Clock.fixed(Instant.parse("2026-07-18T20:00:00Z"),ZoneOffset.UTC));
 }
 private java.util.List<String> activeCodes(String number){
  return jdbc.queryForList("""
    select a.CODIGO from CT_OT_ACTIVIDAD ota join CT_ACTIVIDAD a on a.ID_ACTIVIDAD=ota.ID_ACTIVIDAD
    join CT_ORDEN_TRABAJO ot on ot.ID_ORDEN_TRABAJO=ota.ID_ORDEN_TRABAJO
    where ot.NRO_OT=? and ota.ACTIVO='S' order by ota.ID_OT_ACTIVIDAD
    """,String.class,number);
 }
}
