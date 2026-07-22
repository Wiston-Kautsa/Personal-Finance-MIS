package com.wk.pfmis;

import com.wk.pfmis.auth.AuthDatabase;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;

/**
 * Development utility for initializing one existing user's private database.
 * Normal application startup should always go through {@link MainApp}.
 */
public class DbBootstrap {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: DbBootstrap <registered-user-id>");
            System.err.println("Create users through PFMIS first; the bootstrap utility never bypasses user ownership.");
            System.exit(2);
        }
        int userId;
        try {
            userId = Integer.parseInt(args[0]);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("The registered user ID must be a number.", exception);
        }

        AuthDatabase authDatabase = AuthDatabase.getInstance();
        authDatabase.initialize();
        SystemUser user = authDatabase.findUserById(userId);
        if (!user.isActive()) {
            throw new IllegalArgumentException("The selected user is inactive.");
        }
        try {
            UserSession.login(user);
            DatabaseHandler.getInstance().initializeDatabase();
            System.out.println("Initialized private PFMIS database for user " + user.getUsername()
                    + " at " + DatabaseHandler.databasePath());
        } finally {
            UserSession.clear();
        }
    }
}
