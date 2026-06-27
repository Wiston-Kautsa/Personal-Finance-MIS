package com.wk.pfmis.models;

public class Goal {
    private final int id;
    private final String goalName;
    private final double targetAmount;
    private final double currentAmount;
    private final double monthlyContribution;
    private final String targetDate;
    private final String status;

    public Goal(int id, String goalName, double targetAmount, double currentAmount, double monthlyContribution, String targetDate, String status) {
        this.id = id;
        this.goalName = goalName;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount;
        this.monthlyContribution = monthlyContribution;
        this.targetDate = targetDate;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getGoalName() {
        return goalName;
    }

    public double getTargetAmount() {
        return targetAmount;
    }

    public double getCurrentAmount() {
        return currentAmount;
    }

    public double getMonthlyContribution() {
        return monthlyContribution;
    }

    public double getRemainingAmount() {
        return Math.max(0, targetAmount - currentAmount);
    }

    public double getMonthsNeeded() {
        if (monthlyContribution <= 0) {
            return 0;
        }
        return Math.ceil(getRemainingAmount() / monthlyContribution);
    }

    public String getTargetDate() {
        return targetDate;
    }

    public String getStatus() {
        return status;
    }
}
