package org.acme.ruleunits.oracle;
import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
/**
 * Spring Data boundary for appending incidents and finding open incidents that can be resolved
 * after a later successful run.
 */
public interface RuleIncidentJpaRepository extends JpaRepository<RuleIncidentEntity,Long> {
 List<RuleIncidentEntity> findByWorkOrderIdAndStatus(Long workOrderId,String status);
}
