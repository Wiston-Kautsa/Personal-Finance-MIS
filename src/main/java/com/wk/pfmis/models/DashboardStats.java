package com.wk.pfmis.models;

public class DashboardStats {
    private final double totalBalance;
    private final double monthlyIncome;
    private final double monthlyExpenses;
    private final int activeAccounts;
    private final int activeProjects;
    private final int activeGoals;
    private final double moneyGivenOut;

    public DashboardStats(double totalBalance, double monthlyIncome, double monthlyExpenses, int activeAccounts, int activeProjects, int activeGoals, double moneyGivenOut) {
        this.totalBalance = totalBalance;
        this.monthlyIncome = monthlyIncome;
        this.monthlyExpenses = monthlyExpenses;
        this.activeAccounts = activeAccounts;
        this.activeProjects = activeProjects;
        this.activeGoals = activeGoals;
        this.moneyGivenOut = moneyGivenOut;
    }

    public double getTotalBalance() {
        return totalBalance;
    }

    public double getMonthlyIncome() {
        return monthlyIncome;
    }

    public double getMonthlyExpenses() {
        return monthlyExpenses;
    }

    public double getMonthlySavings() {
        return monthlyIncome - monthlyExpenses;
    }

    public int getActiveAccounts() {
        return activeAccounts;
    }

    public int getActiveProjects() {
        return activeProjects;
    }

    public int getActiveGoals() {
        return activeGoals;
    }

    public double getMoneyGivenOut() {
        return moneyGivenOut;
    }
}
