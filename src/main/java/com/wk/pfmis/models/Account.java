package com.wk.pfmis.models;

public class Account {
    private final int id;
    private final String accountName;
    private final String accountType;
    private final String currency;
    private final String bankProviderName;
    private final String accountNumber;
    private final double openingBalance;
    private final String openingBalanceDate;
    private final double minimumBalance;
    private final String accountPurpose;
    private final String branchName;
    private final double currentBalance;
    private final String status;
    private final String notes;
    private final String createdAt;
    private final String accountCategory;
    private final String accountSubtype;
    private final Integer communityGroupId;
    private final boolean systemAccount;

    public Account(
            int id,
            String accountName,
            String accountType,
            String currency,
            String bankProviderName,
            String accountNumber,
            double openingBalance,
            String openingBalanceDate,
            double minimumBalance,
            String accountPurpose,
            String branchName,
            double currentBalance,
            String status,
            String notes,
            String createdAt
    ) {
        this(
                id,
                accountName,
                accountType,
                currency,
                bankProviderName,
                accountNumber,
                openingBalance,
                openingBalanceDate,
                minimumBalance,
                accountPurpose,
                branchName,
                currentBalance,
                status,
                notes,
                createdAt,
                inferAccountCategory(accountType),
                "",
                null,
                false
        );
    }

    public Account(
            int id,
            String accountName,
            String accountType,
            String currency,
            String bankProviderName,
            String accountNumber,
            double openingBalance,
            String openingBalanceDate,
            double minimumBalance,
            String accountPurpose,
            String branchName,
            double currentBalance,
            String status,
            String notes,
            String createdAt,
            String accountCategory,
            String accountSubtype,
            Integer communityGroupId
    ) {
        this(
                id,
                accountName,
                accountType,
                currency,
                bankProviderName,
                accountNumber,
                openingBalance,
                openingBalanceDate,
                minimumBalance,
                accountPurpose,
                branchName,
                currentBalance,
                status,
                notes,
                createdAt,
                accountCategory,
                accountSubtype,
                communityGroupId,
                false
        );
    }

    public Account(
            int id,
            String accountName,
            String accountType,
            String currency,
            String bankProviderName,
            String accountNumber,
            double openingBalance,
            String openingBalanceDate,
            double minimumBalance,
            String accountPurpose,
            String branchName,
            double currentBalance,
            String status,
            String notes,
            String createdAt,
            String accountCategory,
            String accountSubtype,
            Integer communityGroupId,
            boolean systemAccount
    ) {
        this.id = id;
        this.accountName = accountName;
        this.accountType = accountType;
        this.currency = currency;
        this.bankProviderName = bankProviderName;
        this.accountNumber = accountNumber;
        this.openingBalance = openingBalance;
        this.openingBalanceDate = openingBalanceDate;
        this.minimumBalance = minimumBalance;
        this.accountPurpose = accountPurpose;
        this.branchName = branchName;
        this.currentBalance = currentBalance;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
        this.accountCategory = accountCategory == null || accountCategory.isBlank() ? inferAccountCategory(accountType) : accountCategory;
        this.accountSubtype = accountSubtype == null ? "" : accountSubtype;
        this.communityGroupId = communityGroupId;
        this.systemAccount = systemAccount;
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

    public String getOpeningBalanceDate() {
        return openingBalanceDate;
    }

    public double getMinimumBalance() {
        return minimumBalance;
    }

    public String getAccountPurpose() {
        return accountPurpose;
    }

    public String getBranchName() {
        return branchName;
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

    public String getAccountCategory() {
        return accountCategory;
    }

    public String getAccountSubtype() {
        return accountSubtype;
    }

    public Integer getCommunityGroupId() {
        return communityGroupId;
    }

    public boolean isSystemAccount() {
        return systemAccount;
    }

    public boolean isCommunitySavingsAccount() {
        return "COMMUNITY_SAVINGS".equalsIgnoreCase(accountType)
                || "COMMUNITY_SAVINGS_INTERNAL".equalsIgnoreCase(accountType)
                || "Community Savings".equalsIgnoreCase(accountType);
    }

    public boolean isLiabilityAccount() {
        return "LIABILITY".equalsIgnoreCase(accountCategory);
    }

    private static String inferAccountCategory(String accountType) {
        String normalized = accountType == null ? "" : accountType.trim().toUpperCase();
        if (normalized.contains("LOAN") || normalized.contains("CREDIT")) {
            return "LIABILITY";
        }
        return "ASSET";
    }

    @Override
    public String toString() {
        return accountName;
    }
}
