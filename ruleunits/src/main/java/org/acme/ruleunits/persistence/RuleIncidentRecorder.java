package org.acme.ruleunits.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for recording a blocked-stage incident independently and resolving open incidents after a
 * successful run. Implementations must not expose stack traces or generated DRL as persisted
 * incident detail.
 */
public interface RuleIncidentRecorder {
    void record(RuleIncident incident);
    void resolveOpenForWorkOrder(long workOrderId, Instant resolvedAt);

    static RuleIncidentRecorder noOp() {
        return new RuleIncidentRecorder() {
            public void record(RuleIncident incident) { }
            public void resolveOpenForWorkOrder(long workOrderId, Instant resolvedAt) { }
        };
    }

    /**
     * Sanitized incident command describing one failed work-order attempt. It carries stable
     * operational identifiers and bounded detail, not an exception, stack trace, or generated DRL.
     */
    record RuleIncident(long workOrderId, String ruleName, String stage,
            String errorCode, String errorDetail, Instant processedAt, UUID attemptId) {
    }
}
