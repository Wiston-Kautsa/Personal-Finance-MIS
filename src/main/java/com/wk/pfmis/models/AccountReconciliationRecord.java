package com.wk.pfmis.models;

public class AccountReconciliationRecord {
    private final int id;
    private final int accountId;
    private final String accountName;
    private final String reconciliationDate;
    private final double systemBalance;
    private final double actualBalance;
    private final double difference;
    private final String status;
    private final String notes;
    private final String createdAt;

    public AccountReconciliationRecord(
            int id,
            int accountId,
            String accountName,
            String reconciliationDate,
            double systemBalance,
            double actualBalance,
            double difference,
            String status,
            String notes,
            String createdAt
    ) {
        this.id = id;
        this.accountId = accountId;
        this.accountName = accountName;
        this.reconciliationDate = reconciliationDate;
        this.systemBalance = systemBalance;
        this.actualBalance = actualBalance;
        this.difference = difference;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getReconciliationDate() {
        return reconciliationDate;
    }

    public double getSystemBalance() {
        return systemBalance;
    }

    public double getActualBalance() {
        return actualBalance;
    }

    public double getDifference() {
        return difference;
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
}
