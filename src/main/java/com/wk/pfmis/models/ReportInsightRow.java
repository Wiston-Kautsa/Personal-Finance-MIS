package com.wk.pfmis.models;

public class ReportInsightRow {
    private final String area;
    private final String item;
    private final double amount;
    private final double comparisonAmount;
    private final double varianceAmount;
    private final double percentage;
    private final String status;
    private final String recommendation;

    public ReportInsightRow(
            String area,
            String item,
            double amount,
            double comparisonAmount,
            double varianceAmount,
            double percentage,
            String status,
            String recommendation
    ) {
        this.area = area;
        this.item = item;
        this.amount = amount;
        this.comparisonAmount = comparisonAmount;
        this.varianceAmount = varianceAmount;
        this.percentage = percentage;
        this.status = status;
        this.recommendation = recommendation;
    }

    public String getArea() {
        return area;
    }

    public String getItem() {
        return item;
    }

    public double getAmount() {
        return amount;
    }

    public double getComparisonAmount() {
        return comparisonAmount;
    }

    public double getVarianceAmount() {
        return varianceAmount;
    }

    public double getPercentage() {
        return percentage;
    }

    public String getStatus() {
        return status;
    }

    public String getRecommendation() {
        return recommendation;
    }
}
