package org.acme.ruleunits.snapshot;

/**
 * Bounded report of cleanup failure while retiring an owned compiled snapshot. It identifies the
 * affected version without retaining KIE resources or verbose diagnostics.
 */
public record SnapshotRetirementFailure(
        String ruleSetName,
        long version,
        String failureType,
        String summary) {}
