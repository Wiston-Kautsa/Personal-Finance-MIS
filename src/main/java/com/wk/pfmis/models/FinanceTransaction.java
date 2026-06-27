package com.wk.pfmis.models;

public class FinanceTransaction {
    private final int id;
    private final String accountName;
    private final String transactionType;
    private final String transactionPurpose;
    private final String transactionStatus;
    private final String categoryName;
    private final String projectName;
    private final String personName;
    private final double amount;
    private final String transactionDate;
    private final String description;
    private final String paymentMethod;
    private final String referenceNumber;

    public FinanceTransaction(
            int id,
            String accountName,
            String transactionType,
            String transactionPurpose,
            String transactionStatus,
            String categoryName,
            String projectName,
            String personName,
            double amount,
            String transactionDate,
            String description,
            String paymentMethod,
            String referenceNumber
    ) {
        this.id = id;
        this.accountName = accountName;
        this.transactionType = transactionType;
        this.transactionPurpose = transactionPurpose;
        this.transactionStatus = transactionStatus;
        this.categoryName = categoryName;
        this.projectName = projectName;
        this.personName = personName;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.referenceNumber = referenceNumber;
    }

    public int getId() {
        return id;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public String getTransactionPurpose() {
        return transactionPurpose;
    }

    public String getTransactionStatus() {
        return transactionStatus;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getPersonName() {
        return personName;
    }

    public double getAmount() {
        return amount;
    }

    public String getTransactionDate() {
        return transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getIncomeStatusLabel() {
        return switch (transactionStatus) {
            case "OPEN" -> "Pending";
            case "CANCELLED" -> "Cancelled";
            default -> "Received";
        };
    }
}
