package org.acme.ruleunits.snapshot;

/**
 * Signals that no valid compiled snapshot can currently be leased. It covers initial absence and
 * shutdown, while failed refreshes continue serving an existing last-known-good snapshot.
 */
public final class RuleExecutionUnavailableException extends IllegalStateException {
    public RuleExecutionUnavailableException() {
        super("No compiled rule-set snapshot is currently available");
    }
}
