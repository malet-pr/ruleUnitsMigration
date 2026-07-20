package org.acme.ruleunits.oracle;
import org.acme.ruleunits.catalog.ActivityCatalog; import org.springframework.stereotype.Component;
/**
 * Oracle implementation of the activity-validation boundary. It answers existence and active
 * status without exposing catalog entities to domain or rule code.
 */
@Component
public class OracleActivityCatalog implements ActivityCatalog {
 private final ActivityDefinitionJpaRepository repository;
 public OracleActivityCatalog(ActivityDefinitionJpaRepository repository){this.repository=repository;}
 public boolean existsAndIsActive(String code){return repository.findByCode(code).map(ActivityDefinitionEntity::isActive).orElse(false);}
}
