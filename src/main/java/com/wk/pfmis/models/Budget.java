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
        this.id = id;
        this.budgetName = budgetName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.budgetMonth = budgetMonth;
        this.amountLimit = amountLimit;
        this.rollover = rollover;
        this.status = status;
        this.notes = notes;
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

    @Override
    public String toString() {
        return budgetName;
    }
}
