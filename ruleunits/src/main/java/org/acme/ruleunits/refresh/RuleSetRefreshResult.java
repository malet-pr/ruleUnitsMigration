package org.acme.ruleunits.refresh;

import java.util.Objects;

/**
 * Sanitized internal report for a refresh attempt, including version, phase, correlation ID, and
 * publication status. It is safe to translate to the administrative response but not a replacement
 * for full diagnostic logs.
 */
public record RuleSetRefreshResult(
        String ruleSetName,
        Long attemptedVersion,
        String correlationId,
        RuleSetRefreshStatus status,
        RuleSetRefreshPhase failurePhase,
        String failureType,
        String summary) {

    public RuleSetRefreshResult {
        Objects.requireNonNull(ruleSetName);
        Objects.requireNonNull(correlationId);
        Objects.requireNonNull(status);
        if (status == RuleSetRefreshStatus.PUBLISHED) {
            Objects.requireNonNull(attemptedVersion);
            if (failurePhase != null || failureType != null || summary != null) {
                throw new IllegalArgumentException("Published refresh result cannot contain failure data");
            }
        } else {
            Objects.requireNonNull(failurePhase);
            Objects.requireNonNull(failureType);
            Objects.requireNonNull(summary);
        }
    }

    public static RuleSetRefreshResult published(
            String ruleSetName, long version, String correlationId) {
        return new RuleSetRefreshResult(
                ruleSetName, version, correlationId, RuleSetRefreshStatus.PUBLISHED,
                null, null, null);
    }

    public static RuleSetRefreshResult failed(
            String ruleSetName, Long attemptedVersion, String correlationId,
            RuleSetRefreshPhase phase, Throwable failure) {
        return new RuleSetRefreshResult(
                ruleSetName, attemptedVersion, correlationId, RuleSetRefreshStatus.FAILED,
                phase, failureType(failure), summary(phase));
    }

    public boolean published() {
        return status == RuleSetRefreshStatus.PUBLISHED;
    }

    private static String failureType(Throwable failure) {
        String simpleName = failure.getClass().getSimpleName();
        return simpleName.isBlank() ? "RuntimeException" : simpleName;
    }

    private static String summary(RuleSetRefreshPhase phase) {
        return switch (phase) {
            case LOAD -> "Failed to load active rule-set definition";
            case VALIDATE -> "Loaded rule-set definition failed validation";
            case ASSEMBLE -> "Failed to assemble traditional DRL";
            case COMPILE -> "Failed to compile assembled rule set";
            case PUBLISH -> "Failed to publish compiled rule-set snapshot";
        };
    }
}
