package com.wk.pfmis.models;

public class AuthenticationEventRecord {
    private final long id;
    private final Integer userId;
    private final String username;
    private final String eventType;
    private final String result;
    private final String details;
    private final String createdAt;

    public AuthenticationEventRecord(long id, Integer userId, String username, String eventType,
                                     boolean success, String details, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.username = username == null || username.isBlank() ? "-" : username;
        this.eventType = eventType == null ? "" : eventType;
        this.result = success ? "SUCCESS" : "FAILED";
        this.details = details == null ? "" : details;
        this.createdAt = createdAt == null ? "" : createdAt;
    }

    public long getId() { return id; }
    public Integer getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEventType() { return eventType; }
    public String getResult() { return result; }
    public String getDetails() { return details; }
    public String getCreatedAt() { return createdAt; }
}
