PFMIS MULTI-USER PACKAGE

This package adds first-run administrator setup, Super Administrator-controlled user creation, secure sign-in, private per-user workspaces, Super Administrator user management, workspace switching, password change/reset, failed-login lockout, authentication auditing, per-user AI credentials, and per-user backups.

SECURITY BASELINE
- PFMIS does not ship with a built-in username or password.
- The application must never recreate, reactivate, promote or reset an administrator account during startup.
- The first registered account becomes the Super Administrator only when no users exist.
- Additional users must be created from Manage Users by an active Super Administrator.

FIRST RUN
1. Start PFMIS.
2. Choose Create First Administrator.
3. Enter new credentials for the first account. Existing single-user pfmis.db data is copied into this administrator's workspace automatically only during first-user registration.
4. Use Manage Users to create or administer additional accounts.

RELEASE PACKAGING
PFMIS produces two separate release artifacts:
- Source package: `PFMIS-Source-<timestamp>.zip`
- Windows installer: `PFMIS-<version>-x64.exe`

Use `scripts/package-source-release.ps1` or `scripts/package-release.ps1` to create source archives for review and handoff. A source archive is not a user installer and does not include Java, JavaFX, SQLite JDBC, JNA or a native launcher.

Use `scripts/build-windows-installer.ps1` on Windows with JDK 21, Maven, jpackage and WiX to create the self-contained Windows 10/11 x64 installer. The installed shortcuts must launch the native jpackage `PFMIS.exe` GUI launcher directly. They must not launch `run-pfmis.bat`, Maven, `cmd.exe`, PowerShell or a console Java wrapper.

Release packages must not contain `.env` variants, SQLite/database files, lock files, backups, logs, personal reports, generated exports, local AI model files, nested archives or credentials. Use `.env.example` as the configuration template.

If any Gmail App Password, SMTP/IMAP password or external AI API key was ever exposed in a shared project copy or release archive, rotate it at the provider before using PFMIS again.

DATA LOCATION
- User registry: operating-system PFMIS data folder/pfmis-auth.db
- Financial workspace: operating-system PFMIS data folder/users/<user-id>/pfmis.db
- User backups: operating-system PFMIS data folder/users/<user-id>/backups/
- User-registry backup: operating-system PFMIS data folder/security-backups/

Normal users cannot switch to another user's workspace. A Super Administrator can open a selected user's workspace, and that access is recorded in the authentication log.
