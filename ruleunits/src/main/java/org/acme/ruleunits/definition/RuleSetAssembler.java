package org.acme.ruleunits.definition;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Early template-family assembler for parameterized replacement rules. It demonstrates safe token
 * substitution for structured inputs; the dynamic Oracle runtime uses the broader traditional DRL
 * renderer instead.
 */
public final class RuleSetAssembler {

    public static final String PACKAGE_TOKEN = "{{package}}";
    public static final String UNIT_TOKEN = "{{unit}}";
    public static final String RULES_TOKEN = "{{rules}}";

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");
    private static final Pattern SAFE_CODE = Pattern.compile("[A-Za-z0-9_-]+");

    public String assemble(
            RuleTemplate template,
            String packageName,
            String unitName,
            List<TemplateRuleDefinition> definitions) {
        requireSafe(SAFE_IDENTIFIER, packageName, "package");
        requireSafe(SAFE_IDENTIFIER, unitName, "unit");
        String rules = definitions.stream()
                .sorted(Comparator.comparing(TemplateRuleDefinition::ruleName))
                .map(this::renderRule)
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        return template.drlTemplate()
                .replace(PACKAGE_TOKEN, packageName)
                .replace(UNIT_TOKEN, unitName)
                .replace(RULES_TOKEN, rules);
    }

    private String renderRule(TemplateRuleDefinition definition) {
        requireSafe(SAFE_IDENTIFIER, definition.ruleName(), "rule name");
        requireSafe(SAFE_IDENTIFIER, definition.ruleType(), "rule type");
        requireSafe(SAFE_CODE, definition.action().oldCode(), "old activity code");
        requireSafe(SAFE_CODE, definition.action().newCode(), "new activity code");

        String conditions = definition.requiredActivities().stream()
                .map(RequiredActivityCondition::activityCode)
                .peek(code -> requireSafe(SAFE_CODE, code, "required activity code"))
                .map(code -> "        activeActivityCodes contains \"" + code + "\"")
                .collect(Collectors.joining("," + System.lineSeparator()));

        return """
                rule "%s"
                when
                    WorkOrderEvaluation(
                        workOrderType == WorkOrderType.%s,
                %s
                    ) from entry-point "workOrders"
                then
                    actions.add(new ReplaceActivity("%s", "%s", "%s", "%s"));
                end
                """.formatted(
                definition.ruleName(),
                definition.workOrderType(),
                conditions,
                definition.action().oldCode(),
                definition.action().newCode(),
                definition.ruleType(),
                definition.ruleName()).strip();
    }

    private static void requireSafe(Pattern pattern, String value, String field) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Unsafe " + field + ": " + value);
        }
    }
}
