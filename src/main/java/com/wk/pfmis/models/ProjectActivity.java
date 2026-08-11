package com.wk.pfmis.models;

public class ProjectActivity {
    private final int id;
    private final int projectId;
    private final String projectName;
    private final String activityName;
    private final String activityType;
    private final String activityDate;
    private final String description;
    private final double plannedCost;
    private final double amountUsed;
    private final String categoryName;
    private final String accountName;
    private final String paymentMethod;
    private final String reason;
    private final String startDate;
    private final String endDate;
    private final String actualCompletionDate;
    private final String responsiblePerson;
    private final String priority;
    private final double progress;
    private final String evidenceReference;
    private final String status;

    public ProjectActivity(
            int id,
            int projectId,
            String projectName,
            String activityName,
            String activityDate,
            String description,
            double amountUsed,
            String categoryName,
            String accountName,
            String paymentMethod,
            String reason,
            String startDate,
            String endDate,
            String status
    ) {
        this(
                id,
                projectId,
                projectName,
                activityName,
                "Other",
                activityDate,
                description,
                0,
                amountUsed,
                categoryName,
                accountName,
                paymentMethod,
                reason,
                startDate,
                endDate,
                null,
                "",
                "Medium",
                status != null && status.equalsIgnoreCase("Completed") ? 100 : 0,
                "",
                status
        );
    }

    public ProjectActivity(
            int id,
            int projectId,
            String projectName,
            String activityName,
            String activityType,
            String activityDate,
            String description,
            double plannedCost,
            double amountUsed,
            String categoryName,
            String accountName,
            String paymentMethod,
            String reason,
            String startDate,
            String endDate,
            String actualCompletionDate,
            String responsiblePerson,
            String priority,
            double progress,
            String evidenceReference,
            String status
    ) {
        this.id = id;
        this.projectId = projectId;
        this.projectName = projectName;
        this.activityName = activityName;
        this.activityType = activityType;
        this.activityDate = activityDate;
        this.description = description;
        this.plannedCost = plannedCost;
        this.amountUsed = amountUsed;
        this.categoryName = categoryName;
        this.accountName = accountName;
        this.paymentMethod = paymentMethod;
        this.reason = reason;
        this.startDate = startDate;
        this.endDate = endDate;
        this.actualCompletionDate = actualCompletionDate;
        this.responsiblePerson = responsiblePerson;
        this.priority = priority;
        this.progress = progress;
        this.evidenceReference = evidenceReference;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public int getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getActivityName() {
        return activityName;
    }

    public String getActivityType() {
        return activityType;
    }

    public String getActivityDate() {
        return activityDate;
    }

    public String getDescription() {
        return description;
    }

    public double getPlannedCost() {
        return plannedCost;
    }

    public double getAmountUsed() {
        return amountUsed;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getReason() {
        return reason;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getActualCompletionDate() {
        return actualCompletionDate;
    }

    public String getResponsiblePerson() {
        return responsiblePerson;
    }

    public String getPriority() {
        return priority;
    }

    public double getProgress() {
        return progress;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public String getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return activityName;
    }
}
