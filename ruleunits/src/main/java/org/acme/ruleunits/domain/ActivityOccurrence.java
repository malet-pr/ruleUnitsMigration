package org.acme.ruleunits.domain;

import java.util.Objects;

/**
 * One occurrence of an activity on a work order, identified independently from its activity code
 * so duplicates remain distinct. Origin records whether the occurrence came from persisted input
 * or a rule, but after a stage is saved both kinds participate equally in later-stage deactivation
 * and matching.
 */
public final class ActivityOccurrence {
    private final long occurrenceId;
    private final String code;
    private final String category;
    private final int quantity;
    private final ActivityOrigin origin;
    private final String creatingRuleType;
    private boolean active;
    private String lastAppliedRule;

    private ActivityOccurrence(long occurrenceId, String code, String category, int quantity,
            ActivityOrigin origin, String creatingRuleType, boolean active, String lastAppliedRule) {
        this.occurrenceId = occurrenceId;
        this.code = Objects.requireNonNull(code);
        this.category = category;
        this.quantity = quantity;
        this.origin = Objects.requireNonNull(origin);
        this.creatingRuleType = creatingRuleType;
        this.active = active;
        this.lastAppliedRule = lastAppliedRule;
    }

    public static ActivityOccurrence original(long id, String code, String category, int quantity) {
        return rehydrate(id, code, category, quantity, ActivityOrigin.ORIGINAL, null, true, null);
    }

    public static ActivityOccurrence ruleCreated(
            long id, String code, String category, int quantity, String ruleType) {
        return rehydrate(id, code, category, quantity, ActivityOrigin.RULE, ruleType, true, null);
    }

    public static ActivityOccurrence rehydrate(long id, String code, String category, int quantity,
            ActivityOrigin origin, String creatingRuleType, boolean active, String lastAppliedRule) {
        return new ActivityOccurrence(id, code, category, quantity, origin, creatingRuleType,
                active, lastAppliedRule);
    }

    public long getOccurrenceId() { return occurrenceId; }
    public String getCode() { return code; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public ActivityOrigin getOrigin() { return origin; }
    public String getCreatingRuleType() { return creatingRuleType; }
    public boolean isActive() { return active; }
    public String getLastAppliedRule() { return lastAppliedRule; }

    public void deactivateBy(String rule) {
        active = false;
        lastAppliedRule = Objects.requireNonNull(rule);
    }
}
