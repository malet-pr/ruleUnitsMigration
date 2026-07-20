package org.acme.ruleunits.compilation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.acme.ruleunits.loading.*;

final class RuntimeRuleSetFixture {
    static final String RULE_TEMPLATE = """
            rule "{{ruleName}}"
            when
            {{when}}
            then
            {{then}}
            end
            """;

    private RuntimeRuleSetFixture() {}

    static RenderedRuleSet renderedRuleSet() {
        return new TraditionalDrlRenderer().render(ruleSet());
    }

    static RenderedRuleSet accumulateRenderedRuleSet() {
        return new TraditionalDrlRenderer().render(accumulateRuleSet());
    }

    static RenderedRuleSet malformedRenderedRuleSet() {
        return new TraditionalDrlRenderer().render(malformedRuleSet());
    }

    static LoadedRuleSetDefinition ruleSet() {
        return new LoadedRuleSetDefinition(
                "ACTIVITY_RULES", 17, time(), time().plusSeconds(1),
                List.of(ra1(), ra2(), ra3()));
    }

    static LoadedRuleSetDefinition accumulateRuleSet() {
        return new LoadedRuleSetDefinition(
                "ACCUMULATE_RULES", 17, time(), time().plusSeconds(1),
                List.of(ra1(), ra2(), ra3Accumulate()));
    }

    static LoadedRuleSetDefinition ruleSetWithMalformedDrl() {
        LoadedRuleSetDefinition valid = ruleSet();
        LoadedRuleStageDefinition oldRa1 = valid.stages().getFirst();
        LoadedRuleDefinition oldRule = oldRa1.rules().getFirst();
        LoadedRuleTemplate brokenTemplate = new LoadedRuleTemplate(
                oldRule.template().key(), oldRule.template().version(), oldRule.template().shape(),
                RULE_TEMPLATE.replace("end", "this is not valid DRL\nend"), true);
        LoadedRuleDefinition brokenRule = new LoadedRuleDefinition(
                oldRule.name(), oldRule.order(), oldRule.workOrderType(), oldRule.jobType(),
                brokenTemplate, oldRule.conditions(), oldRule.actions());
        List<LoadedRuleDefinition> brokenRules = new ArrayList<>(oldRa1.rules());
        brokenRules.set(0, brokenRule);
        LoadedRuleStageDefinition brokenRa1 = new LoadedRuleStageDefinition(
                oldRa1.code(), oldRa1.order(), oldRa1.unitPackage(), oldRa1.unitName(), brokenRules);
        return new LoadedRuleSetDefinition(
                valid.name(), 18, valid.validatedAt(), valid.activatedAt(),
                List.of(brokenRa1, valid.stages().get(1), valid.stages().get(2)));
    }

    static LoadedRuleSetDefinition malformedRuleSet() {
        LoadedRuleDefinition malformed = rule(
                "broken-rule", 1, "FINAL",
                new LoadedRuleTemplate("broken", 1, "REPLACE_REQUIRED_ACTIVITIES", """
                        rule "{{ruleName}}"
                        when
                        {{when}}
                        then
                        {{then}}
                        this is not valid DRL
                        end
                        """, true),
                List.of(condition(1, "REQUIRED_ACTIVITY", "CONTAINS", "6T8121", null)),
                List.of(action(1, "REPLACE_ACTIVITY", "6T8121", "097079", null)));
        return new LoadedRuleSetDefinition(
                "BROKEN_RULES", 18, time(), time().plusSeconds(1),
                List.of(stage("RA1", 1, "org.acme.ruleunits.runtime.ra1",
                        "Ra1RuntimeUnit", List.of(malformed))));
    }

    private static LoadedRuleStageDefinition ra1() {
        LoadedRuleDefinition replacement = rule(
                "RA1-test-1", 1, "FINAL", template("replace", "REPLACE_REQUIRED_ACTIVITIES"),
                List.of(
                        condition(1, "REQUIRED_ACTIVITY", "CONTAINS", "6T8121", null),
                        condition(2, "REQUIRED_ACTIVITY", "CONTAINS", "L81494", null)),
                List.of(action(1, "REPLACE_ACTIVITY", "L81494", "097079", null)));
        LoadedRuleDefinition secondVariant = rule(
                "RA1-test-1-2", 2, "FINAL", template("replace", "REPLACE_REQUIRED_ACTIVITIES"),
                List.of(
                        condition(1, "REQUIRED_ACTIVITY", "CONTAINS", "DS7068", null),
                        condition(2, "REQUIRED_ACTIVITY", "CONTAINS", "KO6502", null)),
                List.of(action(1, "REPLACE_ACTIVITY", "KO6502", "SS8192", null)));
        LoadedRuleDefinition thirdVariant = rule(
                "RA1-test-1-3", 3, "FINAL", template("replace", "REPLACE_REQUIRED_ACTIVITIES"),
                List.of(
                        condition(1, "REQUIRED_ACTIVITY", "CONTAINS", "DS7068", null),
                        condition(2, "REQUIRED_ACTIVITY", "CONTAINS", "G99427", null)),
                List.of(action(1, "REPLACE_ACTIVITY", "G99427", "Q79984", null)));
        LoadedRuleDefinition category = new LoadedRuleDefinition(
                "RA1-test-2", 4, null, "FM3X635",
                template("category", "DEACTIVATE_CATEGORY"),
                List.of(condition(1, "ACTIVE_CATEGORY", "CONTAINS", "CAT2", null)),
                List.of(action(1, "DEACTIVATE_CATEGORY", null, null, "CAT2")));
        return stage("RA1", 1, "org.acme.ruleunits.runtime.ra1", "Ra1RuntimeUnit",
                List.of(replacement, secondVariant, thirdVariant, category));
    }

    private static LoadedRuleStageDefinition ra2() {
        LoadedRuleDefinition refinement = rule(
                "RA2-test-1", 1, null, template("refinement", "DEACTIVATE_ALL_AND_ADD"),
                List.of(
                        condition(1, "JOB_TYPE", "IN", "KPVG961", null),
                        condition(2, "JOB_TYPE", "IN", "FM3X635", null),
                        condition(3, "JOB_TYPE", "IN", "FH1X042", null),
                        condition(4, "JOB_TYPE", "IN", "JM5G513", null)),
                List.of(
                        action(1, "DEACTIVATE_ALL", null, null, null),
                        action(2, "ADD_ACTIVITY", null, "FG2802", null)));
        return stage("RA2", 2, "org.acme.ruleunits.runtime.ra2", "Ra2RuntimeUnit", List.of(refinement));
    }

    private static LoadedRuleStageDefinition ra3() {
        LoadedRuleDefinition correctedCategory = new LoadedRuleDefinition(
                "RA3-test-1", 1, null, "JM5G513",
                template("refinement", "DEACTIVATE_ALL_AND_ADD"),
                List.of(condition(1, "ACTIVE_CATEGORY", "CONTAINS", "CAT3", null)),
                List.of(
                        action(1, "DEACTIVATE_ALL", null, null, null),
                        action(2, "ADD_ACTIVITY", null, "AZ9593", null)));
        return stage("RA3", 3, "org.acme.ruleunits.runtime.ra3", "Ra3RuntimeUnit",
                List.of(correctedCategory));
    }

    private static LoadedRuleStageDefinition ra3Accumulate() {
        LoadedRuleDefinition accumulate = rule(
                "withAccumulate", 1, null, template("accumulate", "ACCUMULATE_LEAVE_ONE"),
                List.of(condition(
                        1, "ACCUMULATED_ACTIVITY", "COUNT_GREATER_THAN", "FG2802", 1L)),
                List.of(action(1, "DEACTIVATE_EXCEPT_ONE", "FG2802", null, null)));
        return stage("RA3", 3, "org.acme.ruleunits.runtime.ra3", "Ra3RuntimeUnit", List.of(accumulate));
    }

    private static LoadedRuleStageDefinition stage(String code, int order, String packageName,
            String unitName, List<LoadedRuleDefinition> rules) {
        return new LoadedRuleStageDefinition(code, order, packageName, unitName, rules);
    }

    private static LoadedRuleDefinition rule(String name, int order, String workOrderType,
            LoadedRuleTemplate template, List<LoadedRuleCondition> conditions,
            List<LoadedRuleAction> actions) {
        return new LoadedRuleDefinition(
                name, order, workOrderType, null, template, conditions, actions);
    }

    private static LoadedRuleTemplate template(String key, String shape) {
        return new LoadedRuleTemplate(key, 1, shape, RULE_TEMPLATE, true);
    }

    private static LoadedRuleCondition condition(
            int position, String type, String operator, String value, Long number) {
        return new LoadedRuleCondition(position, type, operator, value, number);
    }

    private static LoadedRuleAction action(int position, String type,
            String oldCode, String newCode, String category) {
        return new LoadedRuleAction(position, type, oldCode, newCode, category);
    }

    private static LocalDateTime time() {
        return LocalDateTime.parse("2026-07-18T20:00:00");
    }
}
