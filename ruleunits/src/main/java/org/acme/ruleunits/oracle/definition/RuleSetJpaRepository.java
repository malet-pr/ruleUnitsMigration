package org.acme.ruleunits.oracle.definition;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data boundary for loading the active versioned rule-set aggregate in deterministic stage,
 * rule, condition, and action order.
 */
public interface RuleSetJpaRepository extends JpaRepository<RuleSetEntity, Long> {
    Optional<RuleSetEntity> findByNameAndStatus(String name, RuleSetStatus status);

    Optional<RuleSetEntity> findByNameAndVersion(String name, long version);
}
