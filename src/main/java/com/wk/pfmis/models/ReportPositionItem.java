package com.wk.pfmis.models;

public class ReportPositionItem {
    private final int id;
    private final String itemName;
    private final String positionType;
    private final String itemType;
    private final double currentValue;
    private final String valuationDate;
    private final String status;
    private final String notes;

    public ReportPositionItem(
            int id,
            String itemName,
            String positionType,
            String itemType,
            double currentValue,
            String valuationDate,
            String status,
            String notes
    ) {
        this.id = id;
        this.itemName = itemName;
        this.positionType = positionType;
        this.itemType = itemType;
        this.currentValue = currentValue;
        this.valuationDate = valuationDate;
        this.status = status;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public String getItemName() {
        return itemName;
    }

    public String getPositionType() {
        return positionType;
    }

    public String getItemType() {
        return itemType;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public String getValuationDate() {
        return valuationDate;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
