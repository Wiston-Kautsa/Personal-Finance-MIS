package com.wk.pfmis.models;

public class AiInteractionRecord {
    private final int id;
    private final String moduleName;
    private final String actionName;
    private final String providerName;
    private final String status;
    private final String createdAt;

    public AiInteractionRecord(
            int id,
            String moduleName,
            String actionName,
            String providerName,
            String status,
            String createdAt
    ) {
        this.id = id;
        this.moduleName = moduleName;
        this.actionName = actionName;
        this.providerName = providerName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public String getModuleName() {
        return moduleName;
    }

    public String getActionName() {
        return actionName;
    }

    public String getProviderName() {
        return providerName;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
