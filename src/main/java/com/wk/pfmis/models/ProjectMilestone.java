package com.wk.pfmis.models;

public class ProjectMilestone {
    private final int id;
    private final int projectId;
    private final String projectName;
    private final String milestoneName;
    private final String targetDate;
    private final String completionDate;
    private final String status;
    private final String notes;

    public ProjectMilestone(
            int id,
            int projectId,
            String projectName,
            String milestoneName,
            String targetDate,
            String completionDate,
            String status,
            String notes
    ) {
        this.id = id;
        this.projectId = projectId;
        this.projectName = projectName;
        this.milestoneName = milestoneName;
        this.targetDate = targetDate;
        this.completionDate = completionDate;
        this.status = status;
        this.notes = notes;
    }

    public int getId() {
        return id;
    }

    public int getProjectId() {
        return projectId;
    }

    public String getProjectName() {
        return projectName;
    }

    public String getMilestoneName() {
        return milestoneName;
    }

    public String getTargetDate() {
        return targetDate;
    }

    public String getCompletionDate() {
        return completionDate;
    }

    public String getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }
}
