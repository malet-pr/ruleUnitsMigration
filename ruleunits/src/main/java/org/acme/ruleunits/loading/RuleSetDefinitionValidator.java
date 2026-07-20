package org.acme.ruleunits.loading;

import java.util.*;
import java.util.regex.Pattern;
import org.acme.ruleunits.catalog.ActivityCatalog;

/**
 * Validates the complete detached definition before rendering: stage order, positions,
 * identifiers, supported shapes, and action parameters. Every potential addition target must exist
 * and be active at validation time.
 */
public final class RuleSetDefinitionValidator {
    private static final Pattern JAVA_PACKAGE = Pattern.compile(
            "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern RULE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern CODE = Pattern.compile("[A-Za-z0-9_-]+");
    private static final List<String> REQUIRED_STAGES = List.of("RA1", "RA2", "RA3");
    private static final Map<String, String> RUNTIME_UNIT_DATA_CLASSES = Map.of(
            "RA1", "org.acme.ruleunits.runtime.ra1.Ra1RuntimeUnit",
            "RA2", "org.acme.ruleunits.runtime.ra2.Ra2RuntimeUnit",
            "RA3", "org.acme.ruleunits.runtime.ra3.Ra3RuntimeUnit");
    private static final Set<String> SHAPES = Set.of(
            "REPLACE_REQUIRED_ACTIVITIES",
            "DEACTIVATE_CATEGORY",
            "DEACTIVATE_ALL_AND_ADD",
            "ACCUMULATE_LEAVE_ONE");

    private final ActivityCatalog activityCatalog;

    public RuleSetDefinitionValidator(ActivityCatalog activityCatalog) {
        this.activityCatalog = Objects.requireNonNull(activityCatalog);
    }

    public void validate(LoadedRuleSetDefinition ruleSet) {
        List<String> errors = new ArrayList<>();
        require(matches(RULE_NAME, ruleSet.name()), "Unsafe rule-set name", errors);
        require(ruleSet.version() > 0, "Rule-set version must be positive", errors);
        require(ruleSet.validatedAt() != null, "Active rule set has no validation timestamp", errors);
        require(ruleSet.activatedAt() != null, "Active rule set has no activation timestamp", errors);
        validateStages(ruleSet.stages(), errors);
        if (!errors.isEmpty()) {
            throw new RuleDefinitionValidationException(errors);
        }
    }

    private void validateStages(List<LoadedRuleStageDefinition> stages, List<String> errors) {
        require(stages.stream().map(LoadedRuleStageDefinition::code).toList().equals(REQUIRED_STAGES),
                "Active stages must be exactly RA1, RA2, RA3 in that order", errors);
        Set<String> unitDataClasses = new HashSet<>();
        for (int index = 0; index < stages.size(); index++) {
            LoadedRuleStageDefinition stage = stages.get(index);
            String path = "stage " + stage.code();
            require(stage.order() == index + 1, path + " has nonconsecutive order", errors);
            require(matches(JAVA_PACKAGE, stage.unitPackage()), path + " has unsafe unit package", errors);
            require(matches(JAVA_IDENTIFIER, stage.unitName()), path + " has unsafe unit name", errors);
            if (matches(JAVA_PACKAGE, stage.unitPackage())
                    && matches(JAVA_IDENTIFIER, stage.unitName())) {
                require(unitDataClasses.add(stage.unitPackage() + "." + stage.unitName()),
                        path + " reuses another stage's Rule Unit data class", errors);
                require(Objects.equals(RUNTIME_UNIT_DATA_CLASSES.get(stage.code()),
                                stage.unitPackage() + "." + stage.unitName()),
                        path + " must use its dedicated runtime Rule Unit data class", errors);
            }
            require(!stage.rules().isEmpty(), path + " has no active rules", errors);
            validateConsecutive(stage.rules().stream().map(LoadedRuleDefinition::order).toList(),
                    path + " rule", errors);
            stage.rules().forEach(rule -> validateRule(stage, rule, errors));
        }
    }

    private void validateRule(LoadedRuleStageDefinition stage, LoadedRuleDefinition rule,
            List<String> errors) {
        String path = "stage " + stage.code() + ", rule " + rule.name();
        require(matches(RULE_NAME, rule.name()), path + " has unsafe name", errors);
        require(rule.workOrderType() == null || Set.of("FINAL", "ADD").contains(rule.workOrderType()),
                path + " has unsupported work-order type", errors);
        require(rule.jobType() == null || matches(CODE, rule.jobType()),
                path + " has unsafe job type", errors);
        validateTemplate(rule.template(), path, errors);
        require(!rule.conditions().isEmpty(), path + " has no conditions", errors);
        require(!rule.actions().isEmpty(), path + " has no actions", errors);
        validateConsecutive(rule.conditions().stream().map(LoadedRuleCondition::position).toList(),
                path + " condition", errors);
        validateConsecutive(rule.actions().stream().map(LoadedRuleAction::position).toList(),
                path + " action", errors);
        rule.conditions().forEach(condition -> validateCondition(condition, path, errors));
        rule.actions().forEach(action -> validateAction(action, path, errors));
        validateShape(rule, path, errors);
    }

    private void validateTemplate(LoadedRuleTemplate template, String path, List<String> errors) {
        require(template != null, path + " has no template", errors);
        if (template == null) return;
        require(matches(RULE_NAME, template.key()), path + " has unsafe template key", errors);
        require(template.version() > 0, path + " has invalid template version", errors);
        require(template.active(), path + " references an inactive template", errors);
        require(SHAPES.contains(template.shape()), path + " has unsupported template shape", errors);
        require(template.drlTemplate() != null && !template.drlTemplate().isBlank(),
                path + " has an empty DRL template", errors);
    }

    private void validateCondition(LoadedRuleCondition condition, String path, List<String> errors) {
        Map<String, String> supported = Map.of(
                "REQUIRED_ACTIVITY", "CONTAINS",
                "ACTIVE_CATEGORY", "CONTAINS",
                "JOB_TYPE", "IN",
                "ACCUMULATED_ACTIVITY", "COUNT_GREATER_THAN");
        String operator = supported.get(condition.type());
        require(operator != null, path + " has unsupported condition type " + condition.type(), errors);
        require(operator == null || operator.equals(condition.operator()),
                path + " has unsupported operator for " + condition.type(), errors);
        require(matches(CODE, condition.value()), path + " has unsafe condition value", errors);
        if ("ACCUMULATED_ACTIVITY".equals(condition.type())) {
            require(condition.numericValue() != null && condition.numericValue() > 0,
                    path + " accumulate threshold must be positive", errors);
        } else {
            require(condition.numericValue() == null,
                    path + " non-accumulate condition has a numeric value", errors);
        }
    }

    private void validateAction(LoadedRuleAction action, String path, List<String> errors) {
        switch (String.valueOf(action.type())) {
            case "REPLACE_ACTIVITY" -> {
                requireCode(action.oldActivityCode(), path + " replacement source", errors);
                requireAddition(action.newActivityCode(), path + " replacement target", errors);
                require(action.category() == null, path + " replacement has an unexpected category", errors);
            }
            case "ADD_ACTIVITY" -> {
                require(action.oldActivityCode() == null, path + " addition has an old activity", errors);
                requireAddition(action.newActivityCode(), path + " addition target", errors);
                require(action.category() == null, path + " addition has an unexpected category", errors);
            }
            case "DEACTIVATE_CATEGORY" -> {
                requireCode(action.category(), path + " deactivation category", errors);
                require(action.oldActivityCode() == null && action.newActivityCode() == null,
                        path + " category deactivation has activity parameters", errors);
            }
            case "DEACTIVATE_ALL" -> require(noParameters(action),
                    path + " deactivate-all has unexpected parameters", errors);
            case "DEACTIVATE_EXCEPT_ONE" -> {
                requireCode(action.oldActivityCode(), path + " retained activity", errors);
                require(action.newActivityCode() == null && action.category() == null,
                        path + " deactivate-except-one has unexpected parameters", errors);
            }
            default -> errors.add(path + " has unsupported action type " + action.type());
        }
    }

    private void validateShape(LoadedRuleDefinition rule, String path, List<String> errors) {
        String shape = rule.template() == null ? null : rule.template().shape();
        Set<String> conditionTypes = collect(rule.conditions(), LoadedRuleCondition::type);
        Set<String> actionTypes = collect(rule.actions(), LoadedRuleAction::type);
        if ("REPLACE_REQUIRED_ACTIVITIES".equals(shape)) {
            require(conditionTypes.equals(Set.of("REQUIRED_ACTIVITY")),
                    path + " replacement shape requires only activity conditions", errors);
            require(actionTypes.equals(Set.of("REPLACE_ACTIVITY")) && rule.actions().size() == 1,
                    path + " replacement shape requires one replacement action", errors);
        } else if ("DEACTIVATE_CATEGORY".equals(shape)) {
            require(conditionTypes.contains("ACTIVE_CATEGORY"),
                    path + " category shape requires an active-category condition", errors);
            require(actionTypes.equals(Set.of("DEACTIVATE_CATEGORY")),
                    path + " category shape requires category deactivation", errors);
        } else if ("DEACTIVATE_ALL_AND_ADD".equals(shape)) {
            require(actionTypes.equals(Set.of("DEACTIVATE_ALL", "ADD_ACTIVITY"))
                            && rule.actions().size() == 2,
                    path + " refinement shape requires deactivate-all and add", errors);
        } else if ("ACCUMULATE_LEAVE_ONE".equals(shape)) {
            require(conditionTypes.equals(Set.of("ACCUMULATED_ACTIVITY")),
                    path + " accumulate shape requires one accumulate condition family", errors);
            require(actionTypes.equals(Set.of("DEACTIVATE_EXCEPT_ONE")) && rule.actions().size() == 1,
                    path + " accumulate shape requires one deactivate-except-one action", errors);
        }
    }

    private void requireAddition(String code, String field, List<String> errors) {
        requireCode(code, field, errors);
        if (matches(CODE, code)) {
            require(activityCatalog.existsAndIsActive(code),
                    field + " does not exist or is inactive: " + code, errors);
        }
    }

    private static void requireCode(String code, String field, List<String> errors) {
        require(matches(CODE, code), field + " is unsafe or missing", errors);
    }

    private static boolean noParameters(LoadedRuleAction action) {
        return action.oldActivityCode() == null
                && action.newActivityCode() == null
                && action.category() == null;
    }

    private static <T> Set<String> collect(List<T> values, java.util.function.Function<T, String> mapper) {
        Set<String> result = new HashSet<>();
        values.stream().map(mapper).forEach(result::add);
        return result;
    }

    private static void validateConsecutive(List<Integer> positions, String field, List<String> errors) {
        for (int index = 0; index < positions.size(); index++) {
            require(positions.get(index) == index + 1, field + " positions must be consecutive from 1", errors);
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    private static void require(boolean condition, String error, List<String> errors) {
        if (!condition) errors.add(error);
    }
}
