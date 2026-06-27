package com.wk.pfmis.models;

public class Account {
    private final int id;
    private final String accountName;
    private final String accountType;
    private final String currency;
    private final String bankProviderName;
    private final String accountNumber;
    private final double openingBalance;
    private final double currentBalance;
    private final String status;
    private final String notes;
    private final String createdAt;

    public Account(
            int id,
            String accountName,
            String accountType,
            String currency,
            String bankProviderName,
            String accountNumber,
            double openingBalance,
            double currentBalance,
            String status,
            String notes,
            String createdAt
    ) {
        this.id = id;
        this.accountName = accountName;
        this.accountType = accountType;
        this.currency = currency;
        this.bankProviderName = bankProviderName;
        this.accountNumber = accountNumber;
        this.openingBalance = openingBalance;
        this.currentBalance = currentBalance;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getCurrency() {
        return currency;
    }

    public String getBankProviderName() {
        return bankProviderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public double getOpeningBalance() {
        return openingBalance;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return accountName;
    }
}
