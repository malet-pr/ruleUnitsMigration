package org.acme.ruleunits.oracle;
import java.time.Instant;
import org.acme.ruleunits.persistence.RuleIncidentRecorder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

/**
 * Oracle adapter for append-oriented rule incidents and later resolution. Incident writes use
 * their own transaction so a failed stage can be recorded while earlier successful stage commits
 * remain intact.
 */
@Component
public class OracleRuleIncidentRecorder implements RuleIncidentRecorder {
 private final RuleIncidentJpaRepository repository;
 public OracleRuleIncidentRecorder(RuleIncidentJpaRepository repository){this.repository=repository;}

 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void record(RuleIncident incident){
  repository.saveAndFlush(new RuleIncidentEntity(incident.workOrderId(),incident.ruleName(),
    incident.stage(),incident.errorCode(),incident.errorDetail(),incident.processedAt(),
    incident.attemptId().toString()));
 }

 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void resolveOpenForWorkOrder(long workOrderId,Instant resolvedAt){
  var incidents=repository.findByWorkOrderIdAndStatus(workOrderId,"OPEN");
  incidents.forEach(i->i.resolve(resolvedAt));
  repository.saveAllAndFlush(incidents);
 }
}
