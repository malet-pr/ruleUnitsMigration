package org.acme.ruleunits.definition;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;
import org.acme.ruleunits.domain.WorkOrderType;
import org.junit.jupiter.api.Test;

class RuleSetAssemblerTest {

    @Test
    void reproducesTheExecutableRuleSetFromTemplateConditionsAndActions()
            throws IOException {
        List<TemplateRuleDefinition> definitions = List.of(
                definition(
                        "RA1-test-1",
                        List.of("6T8121", "L81494"),
                        "L81494",
                        "097079"),
                definition(
                        "RA1-test-1-2",
                        List.of("DS7068", "KO6502"),
                        "KO6502",
                        "SS8192"),
                definition(
                        "RA1-test-1-3",
                        List.of("DS7068", "G99427"),
                        "G99427",
                        "Q79984"));

        String assembled = assemble(definitions);
        String executable = loadResource("/org/acme/ruleunits/ra1/ra1.drl");

        assertThat(assembled)
                .contains("rule \"RA1-test-1\"")
                .contains("rule \"RA1-test-1-2\"")
                .contains("rule \"RA1-test-1-3\"");
        assertThat(executable)
                .contains("rule \"RA1-test-1\"")
                .contains("rule \"RA1-test-1-2\"")
                .contains("rule \"RA1-test-1-3\"")
                .contains("rule \"RA1-test-2\"");
    }

    @Test
    void supportsAContentOrderedVariableNumberOfActivityConditions()
            throws IOException {
        String drl = assemble(List.of(definition(
                "RA1-test-variable",
                List.of("FIRST", "SECOND", "THIRD"),
                "THIRD",
                "RESULT")));

        assertThat(drl)
                .contains("activeActivityCodes contains \"FIRST\"")
                .contains("activeActivityCodes contains \"SECOND\"")
                .contains("activeActivityCodes contains \"THIRD\"")
                .contains("new ReplaceActivity(\"THIRD\", \"RESULT\"");
    }

    private static String assemble(List<TemplateRuleDefinition> definitions)
            throws IOException {
        RuleTemplate template = new RuleTemplate(
                "REPLACE_REQUIRED_ACTIVITIES",
                loadResource("/org/acme/ruleunits/ra1/ra1-template.drl.txt"));
        return new RuleSetAssembler().assemble(
                template, "org.acme.ruleunits.ra1", "Ra1Unit", definitions);
    }

    private static TemplateRuleDefinition definition(
            String name, List<String> requiredCodes, String oldCode, String newCode) {
        List<RequiredActivityCondition> conditions =
                IntStream.range(0, requiredCodes.size())
                        .mapToObj(index ->
                                new RequiredActivityCondition(index, requiredCodes.get(index)))
                        .toList();
        return new TemplateRuleDefinition(
                name,
                "RA1",
                WorkOrderType.FINAL,
                conditions,
                new ReplacementActionDefinition(oldCode, newCode));
    }

    private static String loadResource(String path) throws IOException {
        try (var input = RuleSetAssemblerTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException("Rule resource not found: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
