package org.acme.ruleunits.oracle;
import jakarta.persistence.*;
/**
 * JPA reference mapping for the unique rule name used to attribute the rule that last changed an
 * activity occurrence.
 */
@Entity @Table(name="CT_REGLA", uniqueConstraints=@UniqueConstraint(name="UK_REGLA_NOMBRE",columnNames="NOMBRE"))
public class RuleEntity {
 @Id @Column(name="ID_REGLA") Long id;
 @Column(name="NOMBRE", nullable=false, unique=true) String name;
 protected RuleEntity() {}
 public String getName(){return name;}
}
