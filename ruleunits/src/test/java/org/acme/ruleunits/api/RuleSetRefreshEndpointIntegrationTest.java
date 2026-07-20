package org.acme.ruleunits.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.acme.ruleunits.oracle.OracleIntegrationTest;
import org.acme.ruleunits.refresh.RuleSetRefreshPhase;
import org.acme.ruleunits.refresh.RuleSetRefreshStatus;
import org.acme.ruleunits.snapshot.RuleExecutionUnavailableException;
import org.acme.ruleunits.snapshot.RuleSetSnapshotManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.jdbc.Sql;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "rulebridge.rules.execution-endpoint-enabled=true")
@Sql(
        scripts = {"/sql/oracle-schema.sql", "/sql/oracle-rule-definition-schema.sql"},
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(
        scripts = {
                "/sql/oracle-clean.sql",
                "/sql/oracle-data.sql",
                "/sql/oracle-stage17-rule-set-data.sql"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RuleSetRefreshEndpointIntegrationTest extends OracleIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired RuleSetSnapshotManager snapshots;
    @Autowired JdbcTemplate jdbc;

    @Test
    void refreshesFromOracleOverRealHttpAndRetainsSnapshotAfterFailure() {
        assertThatThrownBy(snapshots::acquire)
                .isInstanceOf(RuleExecutionUnavailableException.class);

        ResponseEntity<RuleSetRefreshResponse> published = http.postForEntity(
                "/admin/rules/refresh", null, RuleSetRefreshResponse.class);

        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(published.getBody()).isNotNull();
        assertThat(published.getBody().status()).isEqualTo(RuleSetRefreshStatus.PUBLISHED);
        assertThat(published.getBody().ruleSetName()).isEqualTo("ACTIVITY_RULES");
        assertThat(published.getBody().attemptedVersion()).isEqualTo(17L);
        assertThat(published.getBody().correlationId()).isNotBlank();
        try (var lease = snapshots.acquire()) {
            assertThat(lease.ruleSetName()).isEqualTo("ACTIVITY_RULES");
            assertThat(lease.version()).isEqualTo(17L);
        }

        ResponseEntity<Boolean> executed = http.postForEntity(
                "/reglas/correr-reglas?agrupador=A",
                List.of("matching", "missing", "matching", "nonmatching"),
                Boolean.class);

        assertThat(executed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(executed.getBody()).isTrue();
        assertThat(activeCodes("matching")).containsExactly("FG2802");
        assertThat(activityCount("matching", "FG2802")).isEqualTo(1);
        assertThat(activeCodes("nonmatching")).containsExactly("I51434");
        assertThat(jdbc.queryForObject(
                "select count(*) from CT_ORDEN_TRABAJO where NRO_OT='missing'",
                Integer.class)).isZero();

        jdbc.update("""
                update CT_RULE_CONDITION
                set CONDITION_TYPE='UNSUPPORTED'
                where ID_RULE_CONDITION=1741
                """);

        ResponseEntity<RuleSetRefreshResponse> failed = http.postForEntity(
                "/admin/rules/refresh", null, RuleSetRefreshResponse.class);

        assertThat(failed.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(failed.getBody()).isNotNull();
        assertThat(failed.getBody().status()).isEqualTo(RuleSetRefreshStatus.FAILED);
        assertThat(failed.getBody().attemptedVersion()).isEqualTo(17L);
        assertThat(failed.getBody().failurePhase()).isEqualTo(RuleSetRefreshPhase.VALIDATE);
        assertThat(failed.getBody().summary())
                .isEqualTo("Loaded rule-set definition failed validation");
        try (var lease = snapshots.acquire()) {
            assertThat(lease.ruleSetName()).isEqualTo("ACTIVITY_RULES");
            assertThat(lease.version()).isEqualTo(17L);
        }
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
}
