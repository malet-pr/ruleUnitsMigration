package org.acme.ruleunits.api;

import org.acme.ruleunits.refresh.RuleSetRefreshPhase;
import org.acme.ruleunits.refresh.RuleSetRefreshResult;
import org.acme.ruleunits.refresh.RuleSetRefreshStatus;

/**
 * External, sanitized view of a refresh attempt. It carries correlation and phase information
 * needed by operations without exposing generated DRL or verbose KIE diagnostics.
 */
public record RuleSetRefreshResponse(
        String ruleSetName,
        Long attemptedVersion,
        String correlationId,
        RuleSetRefreshStatus status,
        RuleSetRefreshPhase failurePhase,
        String failureType,
        String summary) {

    static RuleSetRefreshResponse from(RuleSetRefreshResult result) {
        return new RuleSetRefreshResponse(
                result.ruleSetName(),
                result.attemptedVersion(),
                result.correlationId(),
                result.status(),
                result.failurePhase(),
                result.failureType(),
                result.summary());
    }
}
