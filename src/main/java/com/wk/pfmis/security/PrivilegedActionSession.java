package com.wk.pfmis.security;

import java.time.Duration;
import java.time.LocalDateTime;

public record PrivilegedActionSession(
        int userId,
        int workspaceId,
        LocalDateTime verifiedAt,
        LocalDateTime expiresAt,
        RiskLevel verificationLevel
) {
    public boolean isValidFor(int requestedUserId, int requestedWorkspaceId, RiskLevel requestedLevel, LocalDateTime now) {
        return userId == requestedUserId
                && workspaceId == requestedWorkspaceId
                && verificationLevel.covers(requestedLevel)
                && expiresAt.isAfter(now);
    }

    public Duration remaining(LocalDateTime now) {
        if (!expiresAt.isAfter(now)) {
            return Duration.ZERO;
        }
        return Duration.between(now, expiresAt);
    }
}
