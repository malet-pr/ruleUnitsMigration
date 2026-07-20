package org.acme.ruleunits.oracle;
import jakarta.persistence.*;
/**
 * JPA reference mapping from a work order to the task code used as the rule-domain job type.
 */
@Entity @Table(name="CT_TAREA")
public class TaskEntity {
 @Id @Column(name="ID_TAREA") Long id;
 @Column(name="CODIGO") String code;
 protected TaskEntity() {}
 public String getCode(){return code;}
}
