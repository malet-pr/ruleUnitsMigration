package org.acme.ruleunits.api;

/**
 * Sanitized HTTP error body for invalid, unknown, or unavailable rule-group execution. It
 * deliberately excludes generated DRL, database details, and compiler diagnostics.
 */
public record RuleExecutionEndpointError(String code, String group) {}
