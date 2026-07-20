package org.acme.ruleunits.loading;

import java.util.List;
import org.acme.ruleunits.oracle.definition.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Oracle adapter that loads the single active rule-set aggregate and maps it to
 * immutable records. No lazy JPA entity escapes the read boundary, and loading performs no
 * validation, rendering, compilation, or publication.
 */
@Service
public class OracleRuleDefinitionLoader implements RuleSetDefinitionSource {
    private final RuleSetJpaRepository repository;

    public OracleRuleDefinitionLoader(RuleSetJpaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public LoadedRuleSetDefinition loadActive(String ruleSetName) {
        RuleSetEntity entity = repository.findByNameAndStatus(ruleSetName, RuleSetStatus.ACTIVE)
                .orElseThrow(() -> new ActiveRuleSetNotFoundException(ruleSetName));
        return map(entity);
    }

    private LoadedRuleSetDefinition map(RuleSetEntity entity) {
        List<LoadedRuleStageDefinition> stages = entity.getStages().stream()
                .filter(RuleStageEntity::isActive)
                .map(this::map)
                .toList();
        return new LoadedRuleSetDefinition(
                entity.getName(), entity.getVersion(), entity.getValidatedAt(),
                entity.getActivatedAt(), stages);
    }

    private LoadedRuleStageDefinition map(RuleStageEntity entity) {
        List<LoadedRuleDefinition> rules = entity.getDefinitions().stream()
                .filter(RuleDefinitionEntity::isActive)
                .map(this::map)
                .toList();
        return new LoadedRuleStageDefinition(
                entity.getCode(), entity.getStageOrder(), entity.getUnitPackage(),
                entity.getUnitName(), rules);
    }

    private LoadedRuleDefinition map(RuleDefinitionEntity entity) {
        RuleTemplateEntity template = entity.getTemplate();
        List<LoadedRuleCondition> conditions = entity.getConditions().stream()
                .map(this::map)
                .toList();
        List<LoadedRuleAction> actions = entity.getActions().stream()
                .map(this::map)
                .toList();
        return new LoadedRuleDefinition(
                entity.getName(), entity.getRuleOrder(), entity.getWorkOrderType(),
                entity.getJobType(),
                new LoadedRuleTemplate(template.getKey(), template.getVersion(),
                        template.getShape(), template.getDrlTemplate(), template.isActive()),
                conditions, actions);
    }

    private LoadedRuleCondition map(RuleConditionEntity entity) {
        return new LoadedRuleCondition(
                entity.getPosition(), entity.getType(), entity.getOperator(),
                entity.getValue(), entity.getNumericValue());
    }

    private LoadedRuleAction map(RuleActionEntity entity) {
        return new LoadedRuleAction(
                entity.getPosition(), entity.getType(), entity.getOldActivityCode(),
                entity.getNewActivityCode(), entity.getCategory());
    }
}
