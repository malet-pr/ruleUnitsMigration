package org.acme.ruleunits.definition;

import java.util.Objects;

/**
 * One positioned required-activity parameter for a shared rule template. A rule may contain any
 * positive number of these rows, preserving database order independently of DRL text.
 */
public record RequiredActivityCondition(int position, String activityCode) {

    public RequiredActivityCondition {
        if (position < 0) {
            throw new IllegalArgumentException("Condition position cannot be negative");
        }
        Objects.requireNonNull(activityCode);
    }
}
