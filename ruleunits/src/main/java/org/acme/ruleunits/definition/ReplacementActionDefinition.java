package org.acme.ruleunits.definition;

import java.util.Objects;

/**
 * Database-ready structured parameters for the replace-activity template family. It separates
 * action data from shared DRL text but does not itself validate catalog state or mutate work
 * orders.
 */
public record ReplacementActionDefinition(String oldCode, String newCode) {

    public ReplacementActionDefinition {
        Objects.requireNonNull(oldCode);
        Objects.requireNonNull(newCode);
    }
}
