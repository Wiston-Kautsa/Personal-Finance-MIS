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

    public Project(int id, String projectName, String description, double plannedBudget, double amountSpent, String startDate, String endDate, String status) {
        this.id = id;
        this.projectName = projectName;
        this.description = description;
        this.plannedBudget = plannedBudget;
        this.amountSpent = amountSpent;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
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

    @Override
    public String toString() {
        return projectName;
    }
}
