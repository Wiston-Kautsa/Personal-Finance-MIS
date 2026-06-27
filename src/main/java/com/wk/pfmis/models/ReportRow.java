package com.wk.pfmis.models;

public class ReportRow {
    private final String label;
    private final double amount;

    public ReportRow(String label, double amount) {
        this.label = label;
        this.amount = amount;
    }

    public String getLabel() {
        return label;
    }

    public double getAmount() {
        return amount;
    }
}
