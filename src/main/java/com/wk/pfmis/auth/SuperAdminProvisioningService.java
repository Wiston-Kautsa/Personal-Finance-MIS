package com.wk.pfmis.auth;

import com.wk.pfmis.config.AppConfig;
import com.wk.pfmis.models.EmailSettings;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.PasswordSecurity;

import java.util.Locale;
import java.util.Optional;

public final class SuperAdminProvisioningService {
    private static final System.Logger LOGGER = System.getLogger(SuperAdminProvisioningService.class.getName());
    private static final SuperAdminProvisioningService INSTANCE = new SuperAdminProvisioningService();

    private final AuthDatabase authDatabase = AuthDatabase.getInstance();

    private SuperAdminProvisioningService() {
    }

    public static SuperAdminProvisioningService getInstance() {
        return INSTANCE;
    }

    public Optional<SystemUser> provisionConfiguredSuperAdministrator() {
        LOGGER.log(System.Logger.Level.INFO, "Checking default Super Administrator provisioning status");
        if (authDatabase.hasActiveSuperAdministrator()) {
            LOGGER.log(System.Logger.Level.INFO, "First administrator exists: true");
            return Optional.empty();
        }

        LOGGER.log(System.Logger.Level.INFO, "First administrator exists: false");
        String email = requiredConfig("PFMIS_SUPER_ADMIN_EMAIL");
        String password = requiredConfig("PFMIS_SUPER_ADMIN_PASSWORD");
        validateEmail(email);
        PasswordSecurity.validatePassword(password);

        String username = usernameFromEmail(email);
        String fullName = AppConfig.get("PFMIS_SUPER_ADMIN_FULL_NAME", "PFMIS Super Administrator");
        SystemUser user = authDatabase.provisionSuperAdministrator(fullName, username, email, password);
        LOGGER.log(System.Logger.Level.INFO, "Default Super Administrator provisioned from local configuration");
        return Optional.of(user);
    }

    private String requiredConfig(String key) {
        String value = AppConfig.get(key, "");
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(key + " must be configured in the local .env before PFMIS can start.");
        }
        return value.trim();
    }

    private void validateEmail(String email) {
        if (!EmailSettings.isEmailLike(email)) {
            throw new IllegalStateException("PFMIS_SUPER_ADMIN_EMAIL must be a valid email address.");
        }
    }

    private String usernameFromEmail(String email) {
        String localPart = email.substring(0, email.indexOf('@')).toLowerCase(Locale.ENGLISH);
        String username = localPart.replaceAll("[^a-z0-9._-]", "");
        if (username.length() < 3) {
            username = "pfmisadmin";
        }
        if (username.length() > 40) {
            username = username.substring(0, 40);
        }
        return username;
    }
}
