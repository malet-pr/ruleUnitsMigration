package org.acme.ruleunits.compilation;

import java.util.*;
import java.util.stream.Collectors;
import org.acme.ruleunits.loading.*;

/**
 * Transforms a validated, detached rule-set definition into deterministic traditional-pattern DRL,
 * including supported accumulate shapes. It does not load Oracle data, compile KIE resources,
 * publish snapshots, or execute rules.
 */
public final class TraditionalDrlRenderer {
    public static final String RULE_NAME_TOKEN = "{{ruleName}}";
    public static final String WHEN_TOKEN = "{{when}}";
    public static final String THEN_TOKEN = "{{then}}";

    public RenderedRuleSet render(LoadedRuleSetDefinition ruleSet) {
        return new RenderedRuleSet(
                ruleSet.name(), ruleSet.version(),
                ruleSet.stages().stream().map(this::renderStage).toList());
    }

    private RenderedRuleStage renderStage(LoadedRuleStageDefinition stage) {
        String rules = stage.rules().stream()
                .map(rule -> renderRule(stage, rule))
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        String drl = header(stage) + System.lineSeparator() + System.lineSeparator() + rules
                + System.lineSeparator();
        String sourcePath = "src/main/resources/"
                + stage.unitPackage().replace('.', '/') + "/runtime-" + stage.code() + ".drl";
        return new RenderedRuleStage(
                stage.code(), stage.unitPackage() + "." + stage.unitName(), sourcePath, drl);
    }

    private String header(LoadedRuleStageDefinition stage) {
        return """
                package %s;
                unit %s;

                import java.util.List;
                import org.acme.ruleunits.domain.WorkOrderEvaluation;
                import org.acme.ruleunits.domain.WorkOrderType;
                import org.acme.ruleunits.action.ReplaceActivity;
                import org.acme.ruleunits.action.DeactivateActivityCategory;
                import org.acme.ruleunits.action.DeactivateAllActivities;
                import org.acme.ruleunits.action.DeactivateActivitiesExceptOne;
                import org.acme.ruleunits.action.AddActivity;
                """.formatted(stage.unitPackage(), stage.unitName()).strip();
    }

    private String renderRule(LoadedRuleStageDefinition stage, LoadedRuleDefinition rule) {
        String template = rule.template().drlTemplate();
        requireToken(template, RULE_NAME_TOKEN, rule);
        requireToken(template, WHEN_TOKEN, rule);
        requireToken(template, THEN_TOKEN, rule);
        return template
                .replace(RULE_NAME_TOKEN, rule.name())
                .replace(WHEN_TOKEN, renderWhen(rule))
                .replace(THEN_TOKEN, renderThen(stage, rule))
                .strip();
    }

    private String renderWhen(LoadedRuleDefinition rule) {
        List<LoadedRuleCondition> accumulate = rule.conditions().stream()
                .filter(condition -> condition.type().equals("ACCUMULATED_ACTIVITY"))
                .toList();
        List<String> constraints = new ArrayList<>();
        if (rule.workOrderType() != null) {
            constraints.add("workOrderType == WorkOrderType." + rule.workOrderType());
        }
        if (rule.jobType() != null) {
            constraints.add("jobType == \"" + rule.jobType() + "\"");
        }
        rule.conditions().stream()
                .filter(condition -> condition.type().equals("REQUIRED_ACTIVITY"))
                .map(condition -> "activeActivityCodes contains \"" + condition.value() + "\"")
                .forEach(constraints::add);
        rule.conditions().stream()
                .filter(condition -> condition.type().equals("ACTIVE_CATEGORY"))
                .map(condition -> "activeActivityCategories contains \"" + condition.value() + "\"")
                .forEach(constraints::add);
        List<String> jobTypes = rule.conditions().stream()
                .filter(condition -> condition.type().equals("JOB_TYPE"))
                .map(LoadedRuleCondition::value)
                .toList();
        if (!jobTypes.isEmpty()) {
            constraints.add("jobType in (" + jobTypes.stream()
                    .map(value -> "\"" + value + "\"")
                    .collect(Collectors.joining(", ")) + ")");
        }

        String binding = accumulate.isEmpty() ? "" : "$activities : activeActivityCodes";
        List<String> patternParts = new ArrayList<>();
        if (!binding.isEmpty()) patternParts.add(binding);
        patternParts.addAll(constraints);
        String workOrderPattern = patternParts.isEmpty()
                ? "WorkOrderEvaluation() from entry-point \"workOrders\""
                : "WorkOrderEvaluation(\n        "
                    + String.join(",\n        ", patternParts)
                    + "\n    ) from entry-point \"workOrders\"";
        if (accumulate.isEmpty()) return "    " + workOrderPattern;
        if (accumulate.size() != 1) {
            throw new DrlRenderingException(
                    "Rule " + rule.name() + " must contain exactly one accumulate condition");
        }
        LoadedRuleCondition condition = accumulate.getFirst();
        return "    " + workOrderPattern + System.lineSeparator()
                + "    List($size : size > " + condition.numericValue() + ") from accumulate (\n"
                + "        String(this == \"" + condition.value() + "\") from $activities;\n"
                + "        collectList()\n"
                + "    )";
    }

    private String renderThen(LoadedRuleStageDefinition stage, LoadedRuleDefinition rule) {
        return rule.actions().stream()
                .map(action -> "    actions.add(" + renderAction(stage.code(), rule.name(), action) + ");")
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private String renderAction(String stage, String ruleName, LoadedRuleAction action) {
        return switch (action.type()) {
            case "REPLACE_ACTIVITY" -> "new ReplaceActivity(\"%s\", \"%s\", \"%s\", \"%s\")"
                    .formatted(action.oldActivityCode(), action.newActivityCode(), stage, ruleName);
            case "DEACTIVATE_CATEGORY" -> "new DeactivateActivityCategory(\"%s\", \"%s\", \"%s\")"
                    .formatted(action.category(), stage, ruleName);
            case "DEACTIVATE_ALL" -> "new DeactivateAllActivities(\"%s\", \"%s\")"
                    .formatted(stage, ruleName);
            case "ADD_ACTIVITY" -> "new AddActivity(\"%s\", \"%s\", \"%s\")"
                    .formatted(action.newActivityCode(), stage, ruleName);
            case "DEACTIVATE_EXCEPT_ONE" ->
                    "new DeactivateActivitiesExceptOne(\"%s\", \"%s\", \"%s\")"
                            .formatted(action.oldActivityCode(), stage, ruleName);
            default -> throw new DrlRenderingException("Unsupported action " + action.type());
        };
    }

    private void requireToken(String template, String token, LoadedRuleDefinition rule) {
        if (!template.contains(token)) {
            throw new DrlRenderingException(
                    "Template " + rule.template().key() + " is missing " + token);
        }
    }
}
