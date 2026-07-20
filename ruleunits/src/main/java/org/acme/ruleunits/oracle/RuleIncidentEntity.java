package org.acme.ruleunits.oracle;
import jakarta.persistence.*; import java.time.Instant;
/**
 * JPA mapping of an operational rule-processing incident. It stores bounded diagnostic context and
 * lifecycle status, never a stack trace or generated DRL.
 */
@Entity @Table(name="CT_OT_RULE_INCIDENT")
public class RuleIncidentEntity {
 @Id @GeneratedValue(strategy=GenerationType.SEQUENCE,generator="incidentSeq")
 @SequenceGenerator(name="incidentSeq",sequenceName="CTS_OT_RULE_INCIDENT",allocationSize=1)
 @Column(name="ID_OT_RULE_INCIDENT") Long id;
 @Column(name="ID_ORDEN_TRABAJO",nullable=false) Long workOrderId;
 @Column(name="RULE_NAME") String ruleName;
 @Column(name="RULE_STAGE",nullable=false) String stage;
 @Column(name="RULE_ERROR_CODE",nullable=false) String errorCode;
 @Column(name="RULE_ERROR_DETAIL",length=1000) String errorDetail;
 @Column(name="RULE_PROCESSED_AT",nullable=false) Instant processedAt;
 @Column(name="STATUS",nullable=false) String status;
 @Column(name="RESOLVED_AT") Instant resolvedAt;
 @Column(name="PROCESSING_ATTEMPT_ID",nullable=false) String attemptId;
 protected RuleIncidentEntity() {}
 RuleIncidentEntity(long wo,String rule,String stage,String code,String detail,Instant at,String attempt){
  workOrderId=wo;ruleName=rule;this.stage=stage;errorCode=code;errorDetail=detail;processedAt=at;status="OPEN";attemptId=attempt;
 }
 public Long getId(){return id;} public Long getWorkOrderId(){return workOrderId;}
 public String getRuleName(){return ruleName;} public String getStage(){return stage;}
 public String getErrorCode(){return errorCode;} public String getStatus(){return status;}
 public String getErrorDetail(){return errorDetail;}
 void resolve(Instant at){status="RESOLVED";resolvedAt=at;}
}
