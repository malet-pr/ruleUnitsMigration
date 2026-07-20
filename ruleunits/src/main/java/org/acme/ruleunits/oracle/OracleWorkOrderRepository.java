package org.acme.ruleunits.oracle;

import java.util.*;
import org.acme.ruleunits.domain.*;
import org.acme.ruleunits.persistence.*;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Oracle adapter between the persistence-neutral work-order record and the legacy JPA aggregate.
 * It eagerly loads the required graph, preserves occurrence rows and attribution, flushes each
 * stage save, and copies generated activity IDs back to the record.
 */
@Repository
public class OracleWorkOrderRepository implements WorkOrderRepository {
 private final WorkOrderJpaRepository workOrders;
 private final ActivityDefinitionJpaRepository activities;
 private final RuleTypeJpaRepository ruleTypes;
 private final RuleJpaRepository rules;

 public OracleWorkOrderRepository(WorkOrderJpaRepository workOrders,
   ActivityDefinitionJpaRepository activities, RuleTypeJpaRepository ruleTypes, RuleJpaRepository rules){
  this.workOrders=workOrders;this.activities=activities;this.ruleTypes=ruleTypes;this.rules=rules;
 }

 @Transactional(readOnly=true)
 public Optional<WorkOrderRecord> findByNumber(String number){return workOrders.findByNumber(number).map(this::toRecord);}

 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public WorkOrderRecord save(WorkOrderRecord record){
  WorkOrderEntity entity=workOrders.findById(record.getId()).orElseThrow();
  Map<Long,WorkOrderActivityEntity> existing=new HashMap<>();
  entity.getActivities().forEach(a->existing.put(a.getId(),a));
  Map<ActivityRecord,WorkOrderActivityEntity> mapped = new IdentityHashMap<>();
  for(ActivityRecord source:record.getActivities()){
   WorkOrderActivityEntity target=source.getPersistenceId()==null?null:existing.get(source.getPersistenceId());
   if(target==null){target=new WorkOrderActivityEntity();entity.add(target);}
   ActivityDefinitionEntity activity=activities.findByCode(source.getCode()).orElseThrow();
   RuleTypeEntity type=source.getCreatingRuleType()==null?null:
     ruleTypes.findByShortName(source.getCreatingRuleType()).orElseThrow();
   RuleEntity rule=source.getLastAppliedRule()==null?null:
     rules.findByName(source.getLastAppliedRule()).orElseThrow();
   target.update(source.isActive(),source.getQuantity(),activity,type,rule);
   mapped.put(source,target);
  }
  workOrders.saveAndFlush(entity);
  mapped.forEach((source,target) -> source.assignPersistenceId(target.getId()));
  return record;
 }

 private WorkOrderRecord toRecord(WorkOrderEntity entity){
  List<ActivityRecord> rows=entity.getActivities().stream().map(this::toRecord).toList();
  WorkOrderType type="F".equals(entity.getType())?WorkOrderType.FINAL:WorkOrderType.ADD;
  return new WorkOrderRecord(entity.getId(),entity.getNumber(),entity.getTask().getCode(),type,rows);
 }

 private ActivityRecord toRecord(WorkOrderActivityEntity entity){
  ActivityOrigin origin=entity.getCreatingRuleType()==null?ActivityOrigin.ORIGINAL:ActivityOrigin.RULE;
  return new ActivityRecord(entity.getId(),entity.getId(),entity.getActivity().getCode(),
    entity.getActivity().getCategory(),entity.getQuantity(),origin,
    entity.getCreatingRuleType()==null?null:entity.getCreatingRuleType().getShortName(),
    entity.isActive(),entity.getLastAppliedRule()==null?null:entity.getLastAppliedRule().getName());
 }
}
