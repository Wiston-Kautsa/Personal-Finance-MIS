package com.wk.pfmis.models;

public class FinanceTransaction {
    private final int id;
    private final String accountName;
    private final String transactionType;
    private final String transactionPurpose;
    private final String transactionStatus;
    private final String categoryName;
    private final String projectName;
    private final Integer projectActivityId;
    private final String projectActivityName;
    private final String personName;
    private final double amount;
    private final String transactionDate;
    private final String description;
    private final String paymentMethod;
    private final String referenceNumber;
    private final String createdAt;
    private final Integer loanId;
    private final Integer loanInstallmentId;

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
        this(
                id,
                accountName,
                transactionType,
                transactionPurpose,
                transactionStatus,
                categoryName,
                projectName,
                personName,
                amount,
                transactionDate,
                description,
                paymentMethod,
                referenceNumber,
                null,
                null,
                null,
                null,
                null
        );
    }

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
            String referenceNumber,
            Integer projectActivityId,
            String projectActivityName
    ) {
        this(
                id,
                accountName,
                transactionType,
                transactionPurpose,
                transactionStatus,
                categoryName,
                projectName,
                personName,
                amount,
                transactionDate,
                description,
                paymentMethod,
                referenceNumber,
                projectActivityId,
                projectActivityName,
                null,
                null,
                null
        );
    }

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
            String referenceNumber,
            Integer projectActivityId,
            String projectActivityName,
            String createdAt,
            Integer loanId,
            Integer loanInstallmentId
    ) {
        this.id = id;
        this.accountName = accountName;
        this.transactionType = transactionType;
        this.transactionPurpose = transactionPurpose;
        this.transactionStatus = transactionStatus;
        this.categoryName = categoryName;
        this.projectName = projectName;
        this.projectActivityId = projectActivityId;
        this.projectActivityName = projectActivityName;
        this.personName = personName;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.referenceNumber = referenceNumber;
        this.createdAt = createdAt;
        this.loanId = loanId;
        this.loanInstallmentId = loanInstallmentId;
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

    public Integer getProjectActivityId() {
        return projectActivityId;
    }

    public String getProjectActivityName() {
        return projectActivityName;
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

    public String getCreatedAt() {
        return createdAt;
    }

    public Integer getLoanId() {
        return loanId;
    }

    public Integer getLoanInstallmentId() {
        return loanInstallmentId;
    }

    public String getIncomeStatusLabel() {
        return switch (transactionStatus) {
            case "OPEN" -> "Draft";
            case "CANCELLED" -> "Cancelled";
            case "REVERSED" -> "Reversed";
            default -> "Posted";
        };
    }
}
