package com.wk.pfmis.models;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class BudgetProgress {
    private final Budget budget;
    private final double spent;
    private final double householdUnits;

    public BudgetProgress(Budget budget, double spent) {
        this(budget, spent, 0);
    }

    public BudgetProgress(Budget budget, double spent, double householdUnits) {
        this.budget = budget;
        this.spent = spent;
        this.householdUnits = Math.max(0, householdUnits);
    }

    public int getId() {
        return budget.getId();
    }

    public String getBudgetName() {
        return budget.getBudgetName();
    }

    public Integer getCategoryId() {
        return budget.getCategoryId();
    }

    public String getCategoryName() {
        return budget.getCategoryName();
    }

    public String getBudgetMonth() {
        return budget.getBudgetMonth();
    }

    public double getAmountLimit() {
        return budget.getAmountLimit();
    }

    public double getSpent() {
        return spent;
    }

    public double getRemaining() {
        return budget.getAmountLimit() - spent;
    }

    public double getPercentUsed() {
        if (budget.getAmountLimit() <= 0) {
            return 0;
        }
        return (spent / budget.getAmountLimit()) * 100;
    }

    public double getHouseholdUnits() {
        return householdUnits;
    }

    public boolean hasHouseholdUnits() {
        return householdUnits > 0;
    }

    public double getEffectiveHouseholdUnits() {
        return householdUnits > 0 ? householdUnits : 1;
    }

    public double getLimitPerPerson() {
        return getAmountLimit() / getEffectiveHouseholdUnits();
    }

    public double getSpentPerPerson() {
        return spent / getEffectiveHouseholdUnits();
    }

    public boolean isRollover() {
        return budget.isRollover();
    }

    public String getStatus() {
        return budget.getStatus();
    }

    public String getPlanStatus() {
        return displayStatus(budget.getStatus());
    }

    public String getMonthResult() {
        String storedStatus = budget.getStatus() == null ? "" : budget.getStatus().trim().toUpperCase(Locale.ENGLISH);
        if ("PAUSED".equals(storedStatus)) {
            return "Paused";
        }
        if ("FULFILLED".equals(storedStatus)) {
            return "Fulfilled";
        }
        if ("NOT_MET".equals(storedStatus)) {
            return "Not Met";
        }
        if (spent > budget.getAmountLimit()) {
            return "Not Met";
        }
        YearMonth month = parseMonth(budget.getBudgetMonth());
        YearMonth currentMonth = YearMonth.now();
        if (month == null) {
            return "Needs Review";
        }
        if (month.isAfter(currentMonth)) {
            return "Planned";
        }
        if (month.isBefore(currentMonth)) {
            return "Fulfilled";
        }
        return "On Budget";
    }

    public String getActionNeeded() {
        return switch (getMonthResult()) {
            case "Not Met" -> "Review spending and adjust the next monthly budget.";
            case "Fulfilled" -> "Close the month or roll unused amount forward.";
            case "On Budget" -> "Keep tracking this month.";
            case "Planned" -> "Ready for the selected month.";
            case "Paused" -> "Budget is paused.";
            default -> "Check budget month and spending.";
        };
    }

    public boolean isFulfilled() {
        return "Fulfilled".equals(getMonthResult());
    }

    public boolean isNotMet() {
        return "Not Met".equals(getMonthResult());
    }

    public boolean isOnBudget() {
        return "On Budget".equals(getMonthResult());
    }

    public String getNotes() {
        return budget.getNotes();
    }

    public Budget getBudget() {
        return budget;
    }

    private YearMonth parseMonth(String value) {
        try {
            return value == null || value.isBlank() ? null : YearMonth.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private String displayStatus(String value) {
        if (value == null || value.isBlank()) {
            return "Planned";
        }
        return switch (value.trim().toUpperCase(Locale.ENGLISH)) {
            case "ACTIVE", "ON_BUDGET" -> "On Budget";
            case "FULFILLED" -> "Fulfilled";
            case "NOT_MET" -> "Not Met";
            case "PAUSED" -> "Paused";
            case "CLOSED" -> "Closed";
            default -> "Planned";
        };
    }
}
