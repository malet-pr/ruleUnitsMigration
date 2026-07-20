package org.acme.ruleunits.oracle;
import jakarta.persistence.*;
/**
 * JPA mapping of the activity catalog row used for code, category, and active-status validation.
 * It is an Oracle persistence detail and does not represent a work-order occurrence.
 */
@Entity @Table(name="CT_ACTIVIDAD")
public class ActivityDefinitionEntity {
 @Id @Column(name="ID_ACTIVIDAD") Long id;
 @Column(name="CODIGO", unique=true) String code;
 @Column(name="CATEGORIA") String category;
 @Column(name="ACTIVO") String active;
 protected ActivityDefinitionEntity() {}
 public Long getId(){return id;} public String getCode(){return code;}
 public String getCategory(){return category;} public boolean isActive(){return "S".equals(active);}
}
