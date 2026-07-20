package org.acme.ruleunits.loading;

/**
 * Source boundary for obtaining the currently active, detached rule-set definition by configured
 * name. Implementations may use Oracle, but consumers remain independent of JPA.
 */
@FunctionalInterface
public interface RuleSetDefinitionSource {
    LoadedRuleSetDefinition loadActive(String ruleSetName);
}
