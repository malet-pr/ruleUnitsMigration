package org.acme.ruleunits.oracle;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
/**
 * Spring Data lookup boundary for resolving unique rule names during work-order activity
 * persistence.
 */
public interface RuleJpaRepository extends JpaRepository<RuleEntity,Long> {
 Optional<RuleEntity> findByName(String name);
}
