package org.acme.ruleunits.oracle;
import jakarta.persistence.*; import java.util.*;
/**
 * JPA aggregate root for the legacy work-order row and its ordered activity occurrences. Rule
 * processing updates activities but intentionally does not model omitted production columns.
 */
@Entity @Table(name="CT_ORDEN_TRABAJO")
public class WorkOrderEntity {
 @Id @Column(name="ID_ORDEN_TRABAJO") Long id;
 @Column(name="NRO_OT",unique=true) String number;
 @Column(name="TIPO_OT") String type;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="ID_TAREA") TaskEntity task;
 @OneToMany(mappedBy="workOrder",cascade=CascadeType.ALL,orphanRemoval=false)
 @OrderBy("id") List<WorkOrderActivityEntity> activities=new ArrayList<>();
 protected WorkOrderEntity() {}
 public Long getId(){return id;} public String getNumber(){return number;}
 public String getType(){return type;} public TaskEntity getTask(){return task;}
 public List<WorkOrderActivityEntity> getActivities(){return activities;}
 void add(WorkOrderActivityEntity activity){activity.attach(this);activities.add(activity);}
}
