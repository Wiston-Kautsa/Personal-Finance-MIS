package com.wk.pfmis.models;

public class GoalStep {
    private final int id;
    private final int goalId;
    private final String goalName;
    private final String stepName;
    private final String description;
    private final double estimatedCost;
    private final double amountReached;
    private final String targetDate;
    private final String status;

    public GoalStep(
            int id,
            int goalId,
            String goalName,
            String stepName,
            String description,
            double estimatedCost,
            double amountReached,
            String targetDate,
            String status
    ) {
        this.id = id;
        this.goalId = goalId;
        this.goalName = goalName;
        this.stepName = stepName;
        this.description = description;
        this.estimatedCost = estimatedCost;
        this.amountReached = amountReached;
        this.targetDate = targetDate;
        this.status = status;
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

    public String getStepName() {
        return stepName;
    }

    public String getDescription() {
        return description;
    }

    public double getEstimatedCost() {
        return estimatedCost;
    }

    public double getAmountReached() {
        return amountReached;
    }

    public double getMissingAmount() {
        return Math.max(0, estimatedCost - amountReached);
    }

    public double getProgressPercent() {
        if (estimatedCost <= 0) {
            return 0;
        }
        return Math.min(100, (amountReached / estimatedCost) * 100);
    }

    public String getTargetDate() {
        return targetDate;
    }

    public String getStatus() {
        return status;
    }
}
