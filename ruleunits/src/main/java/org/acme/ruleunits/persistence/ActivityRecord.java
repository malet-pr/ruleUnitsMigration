package org.acme.ruleunits.persistence;

import org.acme.ruleunits.domain.ActivityOrigin;

/**
 * Persistence-neutral representation of one work-order activity row. It keeps database identity
 * separate from domain occurrence identity so new rows receive generated IDs without collapsing
 * duplicates.
 */
public final class ActivityRecord {
    private Long persistenceId;
    private long domainOccurrenceId;
    private String code;
    private String category;
    private int quantity;
    private ActivityOrigin origin;
    private String creatingRuleType;
    private boolean active;
    private String lastAppliedRule;
    private WorkOrderRecord workOrder;

    public ActivityRecord(Long persistenceId, long domainOccurrenceId, String code,
            String category, int quantity, ActivityOrigin origin, String creatingRuleType,
            boolean active, String lastAppliedRule) {
        this.persistenceId = persistenceId;
        this.domainOccurrenceId = domainOccurrenceId;
        this.code = code;
        this.category = category;
        this.quantity = quantity;
        this.origin = origin;
        this.creatingRuleType = creatingRuleType;
        this.active = active;
        this.lastAppliedRule = lastAppliedRule;
    }

    public Long getPersistenceId() { return persistenceId; }
    public void assignPersistenceId(Long id) {
        if (persistenceId != null && !persistenceId.equals(id)) {
            throw new IllegalStateException("Persistence ID already assigned");
        }
        persistenceId = id;
    }
    public long getDomainOccurrenceId() { return domainOccurrenceId; }
    public String getCode() { return code; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public ActivityOrigin getOrigin() { return origin; }
    public String getCreatingRuleType() { return creatingRuleType; }
    public boolean isActive() { return active; }
    public String getLastAppliedRule() { return lastAppliedRule; }
    public WorkOrderRecord getWorkOrder() { return workOrder; }

    public void setCode(String code) { this.code = code; }
    public void setCategory(String category) { this.category = category; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setOrigin(ActivityOrigin origin) { this.origin = origin; }
    public void setCreatingRuleType(String creatingRuleType) {
        this.creatingRuleType = creatingRuleType;
    }
    public void setActive(boolean active) { this.active = active; }
    public void setLastAppliedRule(String lastAppliedRule) {
        this.lastAppliedRule = lastAppliedRule;
    }
    public void setWorkOrder(WorkOrderRecord workOrder) { this.workOrder = workOrder; }
}
