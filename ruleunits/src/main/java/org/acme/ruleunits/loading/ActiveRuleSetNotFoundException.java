package org.acme.ruleunits.loading;

/**
 * Indicates that Oracle has no single active version for the configured rule-set name. Startup
 * remains possible, but execution is unavailable until a valid snapshot can be published.
 */
public final class ActiveRuleSetNotFoundException extends RuntimeException {
    public ActiveRuleSetNotFoundException(String name) {
        super("No active rule set found: " + name);
    }
}
