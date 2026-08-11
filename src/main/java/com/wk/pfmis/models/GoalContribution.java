package com.wk.pfmis.models;

public class GoalContribution {
    private final int id;
    private final int goalId;
    private final String goalName;
    private final String contributionDate;
    private final double amount;
    private final String currency;
    private final String contributionType;
    private final String sourceAccountName;
    private final String destinationAccountName;
    private final Integer transactionId;
    private final String allocationReference;
    private final String status;
    private final String notes;

    public GoalContribution(
            int id,
            int goalId,
            String goalName,
            String contributionDate,
            double amount,
            String currency,
            String contributionType,
            String sourceAccountName,
            String destinationAccountName,
            Integer transactionId,
            String allocationReference,
            String status,
            String notes
    ) {
        this.id = id;
        this.goalId = goalId;
        this.goalName = goalName;
        this.contributionDate = contributionDate;
        this.amount = amount;
        this.currency = currency;
        this.contributionType = contributionType;
        this.sourceAccountName = sourceAccountName;
        this.destinationAccountName = destinationAccountName;
        this.transactionId = transactionId;
        this.allocationReference = allocationReference;
        this.status = status;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public int getGoalId() {
        return goalId;
    }

    public String getGoalName() {
        return goalName;
    }

    public String getContributionDate() {
        return contributionDate;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getContributionType() {
        return contributionType;
    }

    public String getSourceAccountName() {
        return sourceAccountName;
    }

    public String getDestinationAccountName() {
        return destinationAccountName;
    }

    public Integer getTransactionId() {
        return transactionId;
    }

    public String getAllocationReference() {
        return allocationReference;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
