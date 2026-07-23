package com.wk.pfmis.models;

public class SetupPolicyRecord {
    private final int id;
    private final String policyArea;
    private final String itemName;
    private final String configType;
    private final String conditionText;
    private final String thresholdValue;
    private final String severity;
    private final String recommendation;
    private final String targetScreen;
    private final boolean enabled;
    private final String notes;
    private final String updatedAt;

    public SetupPolicyRecord(
            int id,
            String policyArea,
            String itemName,
            String configType,
            String conditionText,
            String thresholdValue,
            String severity,
            String recommendation,
            String targetScreen,
            boolean enabled,
            String notes,
            String updatedAt
    ) {
        this.id = id;
        this.policyArea = policyArea == null ? "" : policyArea;
        this.itemName = itemName == null ? "" : itemName;
        this.configType = configType == null ? "" : configType;
        this.conditionText = conditionText == null ? "" : conditionText;
        this.thresholdValue = thresholdValue == null ? "" : thresholdValue;
        this.severity = severity == null ? "INFO" : severity;
        this.recommendation = recommendation == null ? "" : recommendation;
        this.targetScreen = targetScreen == null ? "" : targetScreen;
        this.enabled = enabled;
        this.notes = notes == null ? "" : notes;
        this.updatedAt = updatedAt == null ? "" : updatedAt;
    }

    public int getId() {
        return id;
    }

    public String getPolicyArea() {
        return policyArea;
    }

    public String getItemName() {
        return itemName;
    }

    public String getConfigType() {
        return configType;
    }

    public String getConditionText() {
        return conditionText;
    }

    public String getThresholdValue() {
        return thresholdValue;
    }

    public String getSeverity() {
        return severity;
    }

    public String getRecommendation() {
        return recommendation;
    }

    public String getTargetScreen() {
        return targetScreen;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getEnabledDisplay() {
        return enabled ? "Enabled" : "Disabled";
    }

    public String getNotes() {
        return notes;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }
}
