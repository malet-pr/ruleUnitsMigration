package org.acme.ruleunits.oracle;
import jakarta.persistence.*;
/**
 * JPA mapping of one work-order activity occurrence. Separate rows preserve duplicates, active
 * state, creation provenance, and the rule that last changed the occurrence.
 */
@Entity @Table(name="CT_OT_ACTIVIDAD")
public class WorkOrderActivityEntity {
 @Id @GeneratedValue(strategy=GenerationType.SEQUENCE,generator="otActSeq")
 @SequenceGenerator(name="otActSeq",sequenceName="CTS_OT_ACTIVIDAD",allocationSize=1)
 @Column(name="ID_OT_ACTIVIDAD") Long id;
 @Column(name="ACTIVO") String active;
 @Column(name="CANTIDAD") int quantity;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ID_ACTIVIDAD") ActivityDefinitionEntity activity;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ID_ORDEN_TRABAJO") WorkOrderEntity workOrder;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ID_REGLA_TIPO") RuleTypeEntity creatingRuleType;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ID_REGLA_APLICADA") RuleEntity lastAppliedRule;
 protected WorkOrderActivityEntity() {}
 public Long getId(){return id;} public boolean isActive(){return "S".equals(active);}
 public int getQuantity(){return quantity;} public ActivityDefinitionEntity getActivity(){return activity;}
 public RuleTypeEntity getCreatingRuleType(){return creatingRuleType;}
 public RuleEntity getLastAppliedRule(){return lastAppliedRule;}
 void update(boolean value,int qty,ActivityDefinitionEntity activity,RuleTypeEntity type,RuleEntity rule){
  this.active=value?"S":"N";this.quantity=qty;this.activity=activity;this.creatingRuleType=type;this.lastAppliedRule=rule;
 }
 void attach(WorkOrderEntity owner){this.workOrder=owner;}
}
