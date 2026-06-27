package com.wk.pfmis.models;

public class ProjectActivity {
    private final int id;
    private final int projectId;
    private final String projectName;
    private final String activityName;
    private final String activityDate;
    private final String description;
    private final double amountUsed;
    private final String categoryName;
    private final String accountName;
    private final String paymentMethod;
    private final String reason;
    private final String startDate;
    private final String endDate;
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
        this.id = id;
        this.projectId = projectId;
        this.projectName = projectName;
        this.activityName = activityName;
        this.activityDate = activityDate;
        this.description = description;
        this.amountUsed = amountUsed;
        this.categoryName = categoryName;
        this.accountName = accountName;
        this.paymentMethod = paymentMethod;
        this.reason = reason;
        this.startDate = startDate;
        this.endDate = endDate;
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

    public String getActivityDate() {
        return activityDate;
    }

    public String getDescription() {
        return description;
    }

    public double getPlannedCost() {
        return amountUsed;
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

    public String getStatus() {
        return status;
    }
}
