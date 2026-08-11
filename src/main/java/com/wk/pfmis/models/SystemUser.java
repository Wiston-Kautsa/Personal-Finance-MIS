package com.wk.pfmis.models;

public class SystemUser {
    public static final String ROLE_SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_USER = "USER";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    private final int id;
    private final String username;
    private final String fullName;
    private final String email;
    private final String role;
    private final String status;
    private final String createdAt;
    private final String lastLoginAt;
    private final boolean mustChangePassword;

    public SystemUser(int id, String username, String fullName, String email,
                      String role, String status, String createdAt, String lastLoginAt) {
        this(id, username, fullName, email, role, status, createdAt, lastLoginAt, false);
    }

    public SystemUser(int id, String username, String fullName, String email,
                      String role, String status, String createdAt, String lastLoginAt,
                      boolean mustChangePassword) {
        this.id = id;
        this.username = username == null ? "" : username;
        this.fullName = fullName == null ? "" : fullName;
        this.email = email == null ? "" : email;
        this.role = role == null ? ROLE_USER : role;
        this.status = status == null ? STATUS_ACTIVE : status;
        this.createdAt = createdAt == null ? "" : createdAt;
        this.lastLoginAt = lastLoginAt == null ? "" : lastLoginAt;
        this.mustChangePassword = mustChangePassword;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public String getStatus() { return status; }
    public String getCreatedAt() { return createdAt; }
    public String getLastLoginAt() { return lastLoginAt; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public String getPasswordStatus() { return mustChangePassword ? "Change Required" : "Current"; }
    public boolean isSuperAdmin() { return ROLE_SUPER_ADMIN.equals(role); }
    public boolean isAdmin() { return ROLE_ADMIN.equals(role); }
    public boolean isAdminOrSuperAdmin() { return isSuperAdmin() || isAdmin(); }
    public boolean isActive() { return STATUS_ACTIVE.equals(status); }

    public String getRoleDisplay() {
        if (isSuperAdmin()) {
            return "Super Administrator";
        }
        if (isAdmin()) {
            return "Administrator";
        }
        return "User";
    }

    public String getDisplayName() {
        return fullName.isBlank() ? username : fullName;
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + username + ")";
    }
}
