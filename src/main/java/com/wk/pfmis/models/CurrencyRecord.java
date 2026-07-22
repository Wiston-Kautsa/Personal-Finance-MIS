package com.wk.pfmis.models;

public class CurrencyRecord {
    private final int id;
    private final String currencyName;
    private final String currencyCode;
    private final String symbol;
    private final double rateToBase;
    private final boolean baseCurrency;
    private final String status;
    private final String updatedAt;

    public CurrencyRecord(
            int id,
            String currencyName,
            String currencyCode,
            String symbol,
            double rateToBase,
            boolean baseCurrency,
            String status,
            String updatedAt
    ) {
        this.id = id;
        this.currencyName = currencyName;
        this.currencyCode = currencyCode;
        this.symbol = symbol;
        this.rateToBase = rateToBase;
        this.baseCurrency = baseCurrency;
        this.status = status;
        this.updatedAt = updatedAt;
    }

    public int getId() {
        return id;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getRateToBase() {
        return rateToBase;
    }

    public boolean isBaseCurrency() {
        return baseCurrency;
    }

    public String getStatus() {
        return baseCurrency ? "BASE" : status;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
