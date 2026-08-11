package com.wk.pfmis.models;

public class Budget {
    private final int id;
    private final String budgetName;
    private final Integer categoryId;
    private final String categoryName;
    private final String budgetMonth;
    private final double amountLimit;
    private final boolean rollover;
    private final String status;
    private final String notes;
    private final String budgetType;
    private final String startDate;
    private final String endDate;
    private final String currency;
    private final double expectedIncome;
    private final double plannedSavings;
    private final double overallSpendingLimit;

    public Budget(
            int id,
            String budgetName,
            Integer categoryId,
            String categoryName,
            String budgetMonth,
            double amountLimit,
            boolean rollover,
            String status,
            String notes
    ) {
        this(
                id,
                budgetName,
                categoryId,
                categoryName,
                budgetMonth,
                amountLimit,
                rollover,
                status,
                notes,
                "Monthly",
                null,
                null,
                "MWK",
                0,
                0,
                0
        );
    }

    public Budget(
            int id,
            String budgetName,
            Integer categoryId,
            String categoryName,
            String budgetMonth,
            double amountLimit,
            boolean rollover,
            String status,
            String notes,
            String budgetType,
            String startDate,
            String endDate,
            String currency,
            double expectedIncome,
            double plannedSavings,
            double overallSpendingLimit
    ) {
        this.id = id;
        this.budgetName = budgetName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.budgetMonth = budgetMonth;
        this.amountLimit = amountLimit;
        this.rollover = rollover;
        this.status = status;
        this.notes = notes;
        this.budgetType = budgetType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.currency = currency;
        this.expectedIncome = expectedIncome;
        this.plannedSavings = plannedSavings;
        this.overallSpendingLimit = overallSpendingLimit;
    }

    public int getId() {
        return id;
    }

    public String getBudgetName() {
        return budgetName;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getBudgetMonth() {
        return budgetMonth;
    }

    public double getAmountLimit() {
        return amountLimit;
    }

    public boolean isRollover() {
        return rollover;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getBudgetType() {
        return budgetType;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public String getCurrency() {
        return currency;
    }

    public double getExpectedIncome() {
        return expectedIncome;
    }

    public double getPlannedSavings() {
        return plannedSavings;
    }

    public double getOverallSpendingLimit() {
        return overallSpendingLimit;
    }

    @Override
    public String toString() {
        return budgetName;
    }
}
