package com.wk.pfmis.models;

public class Asset {
    private final int id;
    private final String assetName;
    private final String assetCategory;
    private final String acquisitionMethod;
    private final String purchaseDate;
    private final double purchaseCost;
    private final double capitalizedCosts;
    private final String currency;
    private final Integer accountId;
    private final String accountName;
    private final Integer budgetId;
    private final String budgetName;
    private final Integer projectId;
    private final String projectName;
    private final Integer projectActivityId;
    private final String projectActivityName;
    private final Integer purchaseTransactionId;
    private final String paymentTreatment;
    private final String supplier;
    private final String paymentMethod;
    private final String referenceNumber;
    private final String serialNumber;
    private final String location;
    private final String assetCondition;
    private final double quantity;
    private final double currentValue;
    private final String status;
    private final String supportingDocument;
    private final String notes;
    private final String createdAt;

    public Asset(
            int id,
            String assetName,
            String assetCategory,
            String acquisitionMethod,
            String purchaseDate,
            double purchaseCost,
            double capitalizedCosts,
            String currency,
            Integer accountId,
            String accountName,
            Integer budgetId,
            String budgetName,
            Integer projectId,
            String projectName,
            Integer projectActivityId,
            String projectActivityName,
            Integer purchaseTransactionId,
            String paymentTreatment,
            String supplier,
            String paymentMethod,
            String referenceNumber,
            String serialNumber,
            String location,
            String assetCondition,
            double quantity,
            double currentValue,
            String status,
            String supportingDocument,
            String notes,
            String createdAt
    ) {
        this.id = id;
        this.assetName = assetName;
        this.assetCategory = assetCategory;
        this.acquisitionMethod = acquisitionMethod;
        this.purchaseDate = purchaseDate;
        this.purchaseCost = purchaseCost;
        this.capitalizedCosts = capitalizedCosts;
        this.currency = currency;
        this.accountId = accountId;
        this.accountName = accountName;
        this.budgetId = budgetId;
        this.budgetName = budgetName;
        this.projectId = projectId;
        this.projectName = projectName;
        this.projectActivityId = projectActivityId;
        this.projectActivityName = projectActivityName;
        this.purchaseTransactionId = purchaseTransactionId;
        this.paymentTreatment = paymentTreatment;
        this.supplier = supplier;
        this.paymentMethod = paymentMethod;
        this.referenceNumber = referenceNumber;
        this.serialNumber = serialNumber;
        this.location = location;
        this.assetCondition = assetCondition;
        this.quantity = quantity;
        this.currentValue = currentValue;
        this.status = status;
        this.supportingDocument = supportingDocument;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getAssetName() {
        return assetName;
    }

    public String getAssetCategory() {
        return assetCategory;
    }

    public String getAcquisitionMethod() {
        return acquisitionMethod;
    }

    public String getPurchaseDate() {
        return purchaseDate;
    }

    public double getPurchaseCost() {
        return purchaseCost;
    }

    public double getCapitalizedCosts() {
        return capitalizedCosts;
    }

    public double getTotalCost() {
        return purchaseCost + capitalizedCosts;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getAccountId() {
        return accountId;
    }

    public String getAccountName() {
        return accountName;
    }

    public Integer getBudgetId() {
        return budgetId;
    }

    public String getBudgetName() {
        return budgetName;
    }

    public Integer getProjectId() {
        return projectId;
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

    public Integer getPurchaseTransactionId() {
        return purchaseTransactionId;
    }

    public String getPaymentTreatment() {
        return paymentTreatment;
    }

    public String getSupplier() {
        return supplier;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getLocation() {
        return location;
    }

    public String getAssetCondition() {
        return assetCondition;
    }

    public double getQuantity() {
        return quantity;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public String getStatus() {
        return status;
    }

    public String getSupportingDocument() {
        return supportingDocument;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return assetName;
    }
}
