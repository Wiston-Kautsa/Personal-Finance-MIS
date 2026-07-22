package com.wk.pfmis.models;

public class SystemLogRecord {
    private final int id;
    private final String moduleName;
    private final String actionName;
    private final String severity;
    private final String details;
    private final String createdAt;

    public SystemLogRecord(
            int id,
            String moduleName,
            String actionName,
            String severity,
            String details,
            String createdAt
    ) {
        this.id = id;
        this.moduleName = moduleName;
        this.actionName = actionName;
        this.severity = severity;
        this.details = details;
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

    public String getSeverity() {
        return severity;
    }

    public String getDetails() {
        return details;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
