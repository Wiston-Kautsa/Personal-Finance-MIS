PFMIS MULTI-USER PACKAGE

This package adds first-run administrator setup, Super Administrator-controlled user creation, secure sign-in, private per-user workspaces, Super Administrator user management, workspace switching, password change/reset, failed-login lockout, authentication auditing, per-user AI credentials, and per-user backups.

FIRST RUN
1. Start PFMIS.
2. Choose Create First Administrator.
3. Register the first account. Existing single-user pfmis.db data is copied into this administrator's workspace automatically.
4. Use Manage Users to create or administer additional accounts.

DATA LOCATION
- User registry: operating-system PFMIS data folder/pfmis-auth.db
- Financial workspace: operating-system PFMIS data folder/users/<user-id>/pfmis.db
- User backups: operating-system PFMIS data folder/users/<user-id>/backups/
- User-registry backup: operating-system PFMIS data folder/security-backups/

Normal users cannot switch to another user's workspace. A Super Administrator can open a selected user's workspace, and that access is recorded in the authentication log.
