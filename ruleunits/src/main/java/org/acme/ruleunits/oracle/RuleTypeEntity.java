package org.acme.ruleunits.oracle;
import jakarta.persistence.*;
/**
 * JPA reference mapping for the stage or rule type that originally created a work-order activity
 * occurrence.
 */
@Entity @Table(name="CT_REGLA_TIPO")
public class RuleTypeEntity {
 @Id @Column(name="ID_REGLA_TIPO") Long id;
 @Column(name="NOMBRE_CORTO", unique=true) String shortName;
 protected RuleTypeEntity() {}
 public String getShortName(){return shortName;}
}
