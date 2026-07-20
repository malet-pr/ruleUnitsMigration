package org.acme.ruleunits.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.acme.ruleunits.domain.WorkOrderType;

/**
 * Mutable persistence-boundary aggregate shared with repository adapters. It holds activity rows
 * and operational processing metadata without depending on JPA annotations.
 */
public final class WorkOrderRecord {
    private final Long id;
    private final String number;
    private final String jobType;
    private final WorkOrderType workOrderType;
    private final List<ActivityRecord> activities;
    private String externalReference;
    private RuleProcessingStatus ruleProcessingStatus = RuleProcessingStatus.NOT_STARTED;
    private String lastCompletedStage;
    private String failedStage;
    private String ruleErrorCode;
    private String ruleErrorDetail;

    public WorkOrderRecord(String number, String jobType, WorkOrderType workOrderType,
            List<ActivityRecord> activities) {
        this(null, number, jobType, workOrderType, activities);
    }

    public WorkOrderRecord(Long id, String number, String jobType, WorkOrderType workOrderType,
            List<ActivityRecord> activities) {
        this.id = id;
        this.number = Objects.requireNonNull(number);
        this.jobType = Objects.requireNonNull(jobType);
        this.workOrderType = Objects.requireNonNull(workOrderType);
        this.activities = new ArrayList<>(activities);
        this.activities.forEach(activity -> activity.setWorkOrder(this));
    }

    public Long getId() { return id; }
    public String getNumber() { return number; }
    public String getJobType() { return jobType; }
    public WorkOrderType getWorkOrderType() { return workOrderType; }
    public List<ActivityRecord> getActivities() { return activities; }
    public String getExternalReference() { return externalReference; }
    public RuleProcessingStatus getRuleProcessingStatus() { return ruleProcessingStatus; }
    public String getLastCompletedStage() { return lastCompletedStage; }
    public String getFailedStage() { return failedStage; }
    public String getRuleErrorCode() { return ruleErrorCode; }
    public String getRuleErrorDetail() { return ruleErrorDetail; }
    public void setExternalReference(String value) { externalReference = value; }
    public void setRuleProcessingStatus(RuleProcessingStatus value) { ruleProcessingStatus = value; }
    public void setLastCompletedStage(String value) { lastCompletedStage = value; }
    public void setFailedStage(String value) { failedStage = value; }
    public void setRuleErrorCode(String value) { ruleErrorCode = value; }
    public void setRuleErrorDetail(String value) { ruleErrorDetail = value; }
    public void addActivity(ActivityRecord activity) {
        activity.setWorkOrder(this);
        activities.add(activity);
    }
}
