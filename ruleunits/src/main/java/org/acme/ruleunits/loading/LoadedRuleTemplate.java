package org.acme.ruleunits.loading;

/**
 * Detached template revision containing shared traditional DRL fragments for one supported rule
 * shape. It is configuration data, not an executable or independently publishable rule set.
 */
public record LoadedRuleTemplate(
        String key,
        long version,
        String shape,
        String drlTemplate,
        boolean active) {}
