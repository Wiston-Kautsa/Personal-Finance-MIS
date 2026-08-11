package com.wk.pfmis.models;

public class AssetEvent {
    private final int id;
    private final int assetId;
    private final String eventType;
    private final String eventDate;
    private final double amount;
    private final String currency;
    private final String counterparty;
    private final Integer transactionId;
    private final String paymentStatus;
    private final String reason;
    private final String referenceNumber;
    private final String notes;
    private final String createdAt;

    public AssetEvent(
            int id,
            int assetId,
            String eventType,
            String eventDate,
            double amount,
            String currency,
            String counterparty,
            Integer transactionId,
            String paymentStatus,
            String reason,
            String referenceNumber,
            String notes,
            String createdAt
    ) {
        this.id = id;
        this.assetId = assetId;
        this.eventType = eventType;
        this.eventDate = eventDate;
        this.amount = amount;
        this.currency = currency;
        this.counterparty = counterparty;
        this.transactionId = transactionId;
        this.paymentStatus = paymentStatus;
        this.reason = reason;
        this.referenceNumber = referenceNumber;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public int getAssetId() {
        return assetId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventDate() {
        return eventDate;
    }

    public double getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public Integer getTransactionId() {
        return transactionId;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public String getReason() {
        return reason;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public String getNotes() {
        return notes;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
