package com.wk.pfmis.models;

public class Project {
    private final int id;
    private final String projectName;
    private final String description;
    private final double plannedBudget;
    private final double amountSpent;
    private final String startDate;
    private final String endDate;
    private final String status;
    private final String projectType;
    private final String projectOwner;
    private final String priority;
    private final String currency;
    private final String fundingSource;
    private final Integer fundingAccountId;
    private final String fundingAccountName;
    private final Integer linkedGoalId;
    private final String linkedGoalName;
    private final String notes;

    public Project(int id, String projectName, String description, double plannedBudget, double amountSpent, String startDate, String endDate, String status) {
        this(
                id,
                projectName,
                description,
                plannedBudget,
                amountSpent,
                startDate,
                endDate,
                status,
                "Other",
                "",
                "Medium",
                "MWK",
                "",
                null,
                "",
                null,
                "",
                ""
        );
    }

    public Project(
            int id,
            String projectName,
            String description,
            double plannedBudget,
            double amountSpent,
            String startDate,
            String endDate,
            String status,
            String projectType,
            String projectOwner,
            String priority,
            String currency,
            String fundingSource,
            Integer fundingAccountId,
            String fundingAccountName,
            Integer linkedGoalId,
            String linkedGoalName,
            String notes
    ) {
        this.id = id;
        this.projectName = projectName;
        this.description = description;
        this.plannedBudget = plannedBudget;
        this.amountSpent = amountSpent;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.projectType = projectType;
        this.projectOwner = projectOwner;
        this.priority = priority;
        this.currency = currency;
        this.fundingSource = fundingSource;
        this.fundingAccountId = fundingAccountId;
        this.fundingAccountName = fundingAccountName;
        this.linkedGoalId = linkedGoalId;
        this.linkedGoalName = linkedGoalName;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getDescription() {
        return description;
    }

    public double getPlannedBudget() {
        return plannedBudget;
    }

    public double getAmountSpent() {
        return amountSpent;
    }

    public double getRemainingBudget() {
        return plannedBudget - amountSpent;
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

    public String getProjectType() {
        return projectType;
    }

    public String getProjectOwner() {
        return projectOwner;
    }

    public String getPriority() {
        return priority;
    }

    public String getCurrency() {
        return currency;
    }

    public String getFundingSource() {
        return fundingSource;
    }

    public Integer getFundingAccountId() {
        return fundingAccountId;
    }

    public String getFundingAccountName() {
        return fundingAccountName;
    }

    public Integer getLinkedGoalId() {
        return linkedGoalId;
    }

    public String getLinkedGoalName() {
        return linkedGoalName;
    }

    public String getNotes() {
        return notes;
    }

    @Override
    public String toString() {
        return projectName;
    }
}
