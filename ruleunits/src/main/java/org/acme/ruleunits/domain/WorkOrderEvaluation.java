package org.acme.ruleunits.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Mutable in-memory aggregate evaluated by one ordered rule pipeline. Its occurrence list
 * preserves duplicates and accumulated stage results; active-code and active-category views
 * exclude deactivated rows, including for corrected RA3 matching.
 */
public final class WorkOrderEvaluation {

    private final String number;
    private final String jobType;
    private final WorkOrderType workOrderType;
    private final List<ActivityOccurrence> activities;

    public WorkOrderEvaluation(
            String number,
            String jobType,
            WorkOrderType workOrderType,
            List<ActivityOccurrence> activities) {
        this.number = Objects.requireNonNull(number);
        this.jobType = Objects.requireNonNull(jobType);
        this.workOrderType = Objects.requireNonNull(workOrderType);
        this.activities = new ArrayList<>(activities);
    }

    public String getNumber() {
        return number;
    }

    public String getJobType() {
        return jobType;
    }

    public WorkOrderType getWorkOrderType() {
        return workOrderType;
    }

    public List<ActivityOccurrence> getActivities() {
        return activities;
    }

    public List<String> getActiveActivityCodes() {
        return activities.stream()
                .filter(ActivityOccurrence::isActive)
                .map(ActivityOccurrence::getCode)
                .toList();
    }

    public List<String> getActiveActivityCategories() {
        return activities.stream()
                .filter(ActivityOccurrence::isActive)
                .map(ActivityOccurrence::getCategory)
                .filter(Objects::nonNull)
                .toList();
    }
}
