package com.wk.pfmis.security;

import com.wk.pfmis.models.SystemUser;

public final class UserSession {
    private static SystemUser authenticatedUser;
    private static SystemUser workspaceUser;

    private UserSession() {
    }

    public static synchronized void login(SystemUser user) {
        if (user == null || !user.isActive()) {
            throw new IllegalArgumentException("An active user is required.");
        }
        authenticatedUser = user;
        workspaceUser = user;
    }

    public static synchronized void refreshAuthenticatedUser(SystemUser user) {
        requireAuthenticated();
        if (user == null || user.getId() != authenticatedUser.getId()) {
            throw new IllegalArgumentException("Updated user details must match the signed-in account.");
        }
        if (!user.isActive()) {
            throw new IllegalArgumentException("The signed-in account is no longer active.");
        }
        boolean viewingOwnWorkspace = workspaceUser.getId() == authenticatedUser.getId();
        authenticatedUser = user;
        if (viewingOwnWorkspace) {
            workspaceUser = user;
        }
    }

    public static synchronized void switchWorkspace(SystemUser targetUser) {
        requireAuthenticated();
        if (!authenticatedUser.isSuperAdmin()) {
            throw new SecurityException("Only a super administrator can open another user's workspace.");
        }
        if (targetUser == null || !targetUser.isActive()) {
            throw new IllegalArgumentException("Select an active user workspace.");
        }
        workspaceUser = targetUser;
    }

    public static synchronized void returnToOwnWorkspace() {
        requireAuthenticated();
        workspaceUser = authenticatedUser;
    }

    public static synchronized void clear() {
        authenticatedUser = null;
        workspaceUser = null;
    }

    public static synchronized boolean isAuthenticated() {
        return authenticatedUser != null;
    }

    public static synchronized boolean isSuperAdmin() {
        return authenticatedUser != null && authenticatedUser.isSuperAdmin();
    }

    public static synchronized boolean isViewingOwnWorkspace() {
        return authenticatedUser != null && workspaceUser != null && authenticatedUser.getId() == workspaceUser.getId();
    }

    public static synchronized SystemUser getAuthenticatedUser() {
        requireAuthenticated();
        return authenticatedUser;
    }

    public static synchronized SystemUser getWorkspaceUser() {
        requireAuthenticated();
        return workspaceUser;
    }

    public static synchronized int getWorkspaceUserId() {
        return getWorkspaceUser().getId();
    }

    private static void requireAuthenticated() {
        if (authenticatedUser == null || workspaceUser == null) {
            throw new IllegalStateException("Sign in to PFMIS before accessing a personal workspace.");
        }
    }
}
