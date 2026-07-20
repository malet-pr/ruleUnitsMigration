package org.acme.ruleunits.oracle.definition;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data boundary for versioned rule templates used by rule definitions.
 */
public interface RuleTemplateJpaRepository extends JpaRepository<RuleTemplateEntity, Long> {
    Optional<RuleTemplateEntity> findByKeyAndVersion(String key, long version);
}
