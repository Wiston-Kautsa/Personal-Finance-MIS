package com.wk.pfmis.models;

public class RecurringTransactionPlan {
    private final int id;
    private final String planName;
    private final String transactionType;
    private final double amount;
    private final String frequency;
    private final String nextDueDate;
    private final String accountName;
    private final String categoryName;
    private final String projectName;
    private final String status;
    private final String notes;

    public RecurringTransactionPlan(
            int id,
            String planName,
            String transactionType,
            double amount,
            String frequency,
            String nextDueDate,
            String accountName,
            String categoryName,
            String projectName,
            String status,
            String notes
    ) {
        this.id = id;
        this.planName = planName;
        this.transactionType = transactionType;
        this.amount = amount;
        this.frequency = frequency;
        this.nextDueDate = nextDueDate;
        this.accountName = accountName;
        this.categoryName = categoryName;
        this.projectName = projectName;
        this.status = status;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public String getPlanName() {
        return planName;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public double getAmount() {
        return amount;
    }

    public String getFrequency() {
        return frequency;
    }

    public String getNextDueDate() {
        return nextDueDate;
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
