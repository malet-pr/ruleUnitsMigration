package org.acme.ruleunits.loading;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuleSetDefinitionValidatorTest {
    private final RuleSetDefinitionValidator validator =
            new RuleSetDefinitionValidator(Set.of("097079", "FG2802", "AZ9593")::contains);

    @Test
    void rejectsBuildTimeRuleUnitIdentityForRuntimeCompilation() {
        LoadedRuleSetDefinition valid = validRuleSet(false);
        LoadedRuleStageDefinition oldRa1 = valid.stages().getFirst();
        LoadedRuleStageDefinition staticRa1 = new LoadedRuleStageDefinition(
                oldRa1.code(), oldRa1.order(), "org.acme.ruleunits.ra1", "Ra1Unit", oldRa1.rules());
        LoadedRuleSetDefinition colliding = new LoadedRuleSetDefinition(
                valid.name(), valid.version(), valid.validatedAt(), valid.activatedAt(),
                List.of(staticRa1, valid.stages().get(1), valid.stages().get(2)));

        assertThatThrownBy(() -> validator.validate(colliding))
                .isInstanceOf(RuleDefinitionValidationException.class)
                .hasMessageContaining("must use its dedicated runtime Rule Unit data class");
    }

    @Test
    void acceptsTheSupportedSelectedRuleShapesAndAccumulateShape() {
        validator.validate(validRuleSet(false));
        validator.validate(validRuleSet(true));
    }

    @Test
    void rejectsAnInactiveOrMissingActivityBeforePublication() {
        RuleDefinitionValidationException exception = org.assertj.core.api.Assertions.catchThrowableOfType(
                RuleDefinitionValidationException.class,
                () -> validator.validate(ruleSetWithRa2Target("NOT_ACTIVE")));

        org.assertj.core.api.Assertions.assertThat(exception.getErrors())
                .anyMatch(error -> error.contains("does not exist or is inactive: NOT_ACTIVE"));
    }

    @Test
    void rejectsIncompleteStagesUnsafeIdentifiersAndPositionGapsTogether() {
        LoadedRuleStageDefinition invalid = new LoadedRuleStageDefinition(
                "RA1", 2, "not a package", "1Unit",
                List.of(new LoadedRuleDefinition(
                        "bad rule", 2, "UNKNOWN", null,
                        template("REPLACE_REQUIRED_ACTIVITIES"),
                        List.of(new LoadedRuleCondition(
                                2, "REQUIRED_ACTIVITY", "EQUALS", "BAD CODE", null)),
                        List.of(new LoadedRuleAction(
                                2, "REPLACE_ACTIVITY", "L81494", "NOT_ACTIVE", null)))));
        LoadedRuleSetDefinition ruleSet = new LoadedRuleSetDefinition(
                "ACTIVITY_RULES", 17, time(), time(), List.of(invalid));

        assertThatThrownBy(() -> validator.validate(ruleSet))
                .isInstanceOf(RuleDefinitionValidationException.class)
                .hasMessageContaining("exactly RA1, RA2, RA3")
                .hasMessageContaining("unsafe unit package")
                .hasMessageContaining("positions must be consecutive")
                .hasMessageContaining("unsupported work-order type");
    }

    @Test
    void rejectsReusingOneRuleUnitDataClassAcrossStages() {
        LoadedRuleSetDefinition valid = validRuleSet(false);
        LoadedRuleStageDefinition ra1 = valid.stages().getFirst();
        LoadedRuleStageDefinition oldRa2 = valid.stages().get(1);
        LoadedRuleStageDefinition collidingRa2 = new LoadedRuleStageDefinition(
                oldRa2.code(), oldRa2.order(), ra1.unitPackage(), ra1.unitName(), oldRa2.rules());
        LoadedRuleSetDefinition colliding = new LoadedRuleSetDefinition(
                valid.name(), valid.version(), valid.validatedAt(), valid.activatedAt(),
                List.of(ra1, collidingRa2, valid.stages().get(2)));

        assertThatThrownBy(() -> validator.validate(colliding))
                .isInstanceOf(RuleDefinitionValidationException.class)
                .hasMessageContaining("reuses another stage's Rule Unit data class");
    }

    private LoadedRuleSetDefinition validRuleSet(boolean accumulateInRa3) {
        LoadedRuleStageDefinition ra1 = stage("RA1", 1, rule(
                "RA1-test-1", template("REPLACE_REQUIRED_ACTIVITIES"),
                List.of(new LoadedRuleCondition(1, "REQUIRED_ACTIVITY", "CONTAINS", "L81494", null)),
                List.of(new LoadedRuleAction(1, "REPLACE_ACTIVITY", "L81494", "097079", null))));
        LoadedRuleStageDefinition ra2 = stage("RA2", 2, rule(
                "RA2-test-1", template("DEACTIVATE_ALL_AND_ADD"),
                List.of(
                        new LoadedRuleCondition(1, "JOB_TYPE", "IN", "KPVG961", null),
                        new LoadedRuleCondition(2, "JOB_TYPE", "IN", "FM3X635", null),
                        new LoadedRuleCondition(3, "JOB_TYPE", "IN", "FH1X042", null),
                        new LoadedRuleCondition(4, "JOB_TYPE", "IN", "JM5G513", null)),
                List.of(
                        new LoadedRuleAction(1, "DEACTIVATE_ALL", null, null, null),
                        new LoadedRuleAction(2, "ADD_ACTIVITY", null, "FG2802", null))));
        LoadedRuleDefinition ra3Rule = accumulateInRa3
                ? rule("withAccumulate", template("ACCUMULATE_LEAVE_ONE"),
                    List.of(new LoadedRuleCondition(
                            1, "ACCUMULATED_ACTIVITY", "COUNT_GREATER_THAN", "FG2802", 1L)),
                    List.of(new LoadedRuleAction(
                            1, "DEACTIVATE_EXCEPT_ONE", "FG2802", null, null)))
                : rule("RA3-test-1", template("DEACTIVATE_ALL_AND_ADD"),
                    List.of(new LoadedRuleCondition(
                            1, "ACTIVE_CATEGORY", "CONTAINS", "CAT3", null)),
                    List.of(
                            new LoadedRuleAction(1, "DEACTIVATE_ALL", null, null, null),
                            new LoadedRuleAction(2, "ADD_ACTIVITY", null, "AZ9593", null)));
        return new LoadedRuleSetDefinition(
                "ACTIVITY_RULES", 17, time(), time(),
                List.of(ra1, ra2, stage("RA3", 3, ra3Rule)));
    }

    private LoadedRuleSetDefinition ruleSetWithRa2Target(String target) {
        LoadedRuleSetDefinition valid = validRuleSet(false);
        LoadedRuleStageDefinition oldRa2 = valid.stages().get(1);
        LoadedRuleDefinition oldRule = oldRa2.rules().getFirst();
        LoadedRuleDefinition changed = new LoadedRuleDefinition(
                oldRule.name(), oldRule.order(), oldRule.workOrderType(), oldRule.jobType(),
                oldRule.template(), oldRule.conditions(),
                List.of(
                        new LoadedRuleAction(1, "DEACTIVATE_ALL", null, null, null),
                        new LoadedRuleAction(2, "ADD_ACTIVITY", null, target, null)));
        return new LoadedRuleSetDefinition(
                valid.name(), valid.version(), valid.validatedAt(), valid.activatedAt(),
                List.of(valid.stages().getFirst(), stage("RA2", 2, changed), valid.stages().get(2)));
    }

    private LoadedRuleStageDefinition stage(String code, int order, LoadedRuleDefinition rule) {
        return new LoadedRuleStageDefinition(
                code, order, "org.acme.ruleunits.runtime." + code.toLowerCase(),
                code.substring(0, 1) + code.substring(1).toLowerCase() + "RuntimeUnit", List.of(rule));
    }

    private LoadedRuleDefinition rule(String name, LoadedRuleTemplate template,
            List<LoadedRuleCondition> conditions, List<LoadedRuleAction> actions) {
        return new LoadedRuleDefinition(name, 1, null, null, template, conditions, actions);
    }

    private LoadedRuleTemplate template(String shape) {
        return new LoadedRuleTemplate(
                shape.toLowerCase().replace('_', '-'), 1, shape, "traditional DRL", true);
    }

    private LocalDateTime time() {
        return LocalDateTime.parse("2026-07-18T20:00:00");
    }
}
