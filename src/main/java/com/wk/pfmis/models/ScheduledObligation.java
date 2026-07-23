package com.wk.pfmis.models;

public class ScheduledObligation {
    private final int id;
    private final String obligationName;
    private final String obligationType;
    private final double amount;
    private final String dueDate;
    private final String frequency;
    private final String accountName;
    private final String categoryName;
    private final String projectName;
    private final String status;
    private final String notes;

    public ScheduledObligation(
            int id,
            String obligationName,
            String obligationType,
            double amount,
            String dueDate,
            String frequency,
            String accountName,
            String categoryName,
            String projectName,
            String status,
            String notes
    ) {
        this.id = id;
        this.obligationName = obligationName;
        this.obligationType = obligationType;
        this.amount = amount;
        this.dueDate = dueDate;
        this.frequency = frequency;
        this.accountName = accountName;
        this.categoryName = categoryName;
        this.projectName = projectName;
        this.status = status;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public String getObligationName() {
        return obligationName;
    }

    public String getObligationType() {
        return obligationType;
    }

    public double getAmount() {
        return amount;
    }

    public String getDueDate() {
        return dueDate;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
