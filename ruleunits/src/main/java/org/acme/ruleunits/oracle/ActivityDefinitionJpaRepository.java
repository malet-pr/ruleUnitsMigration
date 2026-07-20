package org.acme.ruleunits.oracle;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
/**
 * Spring Data access boundary for activity catalog rows, including lookup by business activity
 * code.
 */
public interface ActivityDefinitionJpaRepository extends JpaRepository<ActivityDefinitionEntity,Long> {
 Optional<ActivityDefinitionEntity> findByCode(String code);
}
