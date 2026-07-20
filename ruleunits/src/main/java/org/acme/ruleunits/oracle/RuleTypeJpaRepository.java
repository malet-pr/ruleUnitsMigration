package org.acme.ruleunits.oracle;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
/**
 * Spring Data lookup boundary for resolving rule-type attribution by its short name.
 */
public interface RuleTypeJpaRepository extends JpaRepository<RuleTypeEntity,Long> {
 Optional<RuleTypeEntity> findByShortName(String name);
}
