package org.acme.ruleunits.oracle;
import java.util.*; import org.springframework.data.jpa.repository.*;
/**
 * Spring Data boundary that fetches the complete work-order graph needed for detached rule
 * evaluation without multiple bag fetches.
 */
public interface WorkOrderJpaRepository extends JpaRepository<WorkOrderEntity,Long> {
 @EntityGraph(attributePaths={"task","activities","activities.activity","activities.creatingRuleType","activities.lastAppliedRule"})
 Optional<WorkOrderEntity> findByNumber(String number);
}
