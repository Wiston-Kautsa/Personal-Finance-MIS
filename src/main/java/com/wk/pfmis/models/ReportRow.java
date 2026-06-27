package com.wk.pfmis.models;

public class ReportRow {
    private final String label;
    private final String account;
    private final double amount;

    public ReportRow(String label, double amount) {
        this(label, "", amount);
    }

    public ReportRow(String label, String account, double amount) {
        this.label = label;
        this.account = account;
        this.amount = amount;
    }

    public String getLabel() {
        return label;
    }

    public String getAccount() {
        return account;
    }

    public double getAmount() {
        return amount;
    }
}
