package com.wk.pfmis.security;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.models.SystemUser;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

public final class PrivilegedActionService {
    private static final PrivilegedActionService INSTANCE = new PrivilegedActionService();
    private static final Duration DEFAULT_VALIDITY = Duration.ofMinutes(5);
    private static final Duration MIN_VALIDITY = Duration.ofMinutes(1);
    private static final Duration MAX_VALIDITY = Duration.ofMinutes(10);

    private final AuthDatabase authDatabase;
    private Duration sessionValidity = DEFAULT_VALIDITY;
    private PrivilegedActionSession session;

    private PrivilegedActionService() {
        this(AuthDatabase.getInstance());
    }

    PrivilegedActionService(AuthDatabase authDatabase) {
        this.authDatabase = authDatabase;
    }

    public static PrivilegedActionService getInstance() {
        return INSTANCE;
    }

    public synchronized VerificationResult verifyCurrentUser(char[] password, RiskLevel level, boolean requireSuperAdmin) {
        if (password == null || password.length == 0 || isBlank(password)) {
            throw new SecurityException("Enter your current password to verify this action.");
        }

        SystemUser signedIn = UserSession.getAuthenticatedUser();
        int workspaceId = UserSession.getWorkspaceUserId();
        String login = signedIn.getUsername().isBlank() ? signedIn.getEmail() : signedIn.getUsername();
        String passwordText = new String(password);
        try {
            SystemUser authenticated = authDatabase.authenticate(login, passwordText);
            if (authenticated.getId() != signedIn.getId()) {
                invalidate();
                throw new SecurityException("Security verification failed for the signed-in user.");
            }
            if (requireSuperAdmin && !authenticated.isSuperAdmin()) {
                invalidate();
                throw new SecurityException("This action requires a Super Administrator account.");
            }
            LocalDateTime verifiedAt = LocalDateTime.now();
            session = new PrivilegedActionSession(
                    signedIn.getId(),
                    workspaceId,
                    verifiedAt,
                    verifiedAt.plus(clampedValidity()),
                    level == null ? RiskLevel.NORMAL : level
            );
            return new VerificationResult(true, sessionStatusText());
        } finally {
            passwordText = "";
            Arrays.fill(password, '\0');
        }
    }

    public synchronized boolean hasValidSession(RiskLevel requestedLevel, boolean forceFreshVerification) {
        if (forceFreshVerification || session == null) {
            return false;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            boolean valid = session.isValidFor(
                    UserSession.getAuthenticatedUser().getId(),
                    UserSession.getWorkspaceUserId(),
                    requestedLevel == null ? RiskLevel.NORMAL : requestedLevel,
                    now
            );
            if (!valid && !session.expiresAt().isAfter(now)) {
                session = null;
            }
            return valid;
        } catch (RuntimeException exception) {
            invalidate();
            return false;
        }
    }

    public synchronized Optional<String> currentStatusText(RiskLevel requestedLevel) {
        if (!hasValidSession(requestedLevel, false)) {
            return Optional.empty();
        }
        return Optional.of(sessionStatusText());
    }

    public synchronized void invalidate() {
        session = null;
    }

    public synchronized void setSessionValidity(Duration sessionValidity) {
        if (sessionValidity == null) {
            this.sessionValidity = DEFAULT_VALIDITY;
            return;
        }
        if (sessionValidity.compareTo(MIN_VALIDITY) < 0) {
            this.sessionValidity = MIN_VALIDITY;
        } else if (sessionValidity.compareTo(MAX_VALIDITY) > 0) {
            this.sessionValidity = MAX_VALIDITY;
        } else {
            this.sessionValidity = sessionValidity;
        }
    }

    private Duration clampedValidity() {
        if (sessionValidity.compareTo(MIN_VALIDITY) < 0) {
            return MIN_VALIDITY;
        }
        if (sessionValidity.compareTo(MAX_VALIDITY) > 0) {
            return MAX_VALIDITY;
        }
        return sessionValidity;
    }

    private String sessionStatusText() {
        if (session == null) {
            return "";
        }
        Duration remaining = session.remaining(LocalDateTime.now());
        long minutes = remaining.toMinutes();
        long seconds = remaining.minusMinutes(minutes).toSeconds();
        return String.format("Security verified - %02d:%02d remaining", minutes, seconds);
    }

    private boolean isBlank(char[] value) {
        for (char character : value) {
            if (!Character.isWhitespace(character)) {
                return false;
            }
        }
        return true;
    }

    public record VerificationResult(boolean verified, String statusText) {
    }
}
