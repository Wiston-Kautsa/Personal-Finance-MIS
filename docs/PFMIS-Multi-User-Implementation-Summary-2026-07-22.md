# PFMIS Multi-User Implementation Summary

## Delivered behaviour

PFMIS now opens with authentication instead of displaying financial information immediately.

1. On first use, the system permits creation of one initial account. That account becomes the **Super Administrator**.
2. After initial setup, public self-registration is disabled. Additional users are created from **Manage Users** by a Super Administrator.
3. Each user has a physically separate SQLite financial database and backup directory.
4. A normal user is bound to his or her own workspace and cannot switch to another user's workspace.
5. A Super Administrator can list users, create accounts, activate or deactivate users, reset passwords, review authentication events, and open any active user's workspace.
6. The application header always displays both the signed-in identity and the active workspace, so administrative access is visible.
7. Authentication, logout, password, status and workspace-access events are recorded in the central security registry.

## Local data layout

```text
PFMIS application-data directory/
├── pfmis-auth.db
├── security-backups/
│   ├── pfmis-auth-latest-daily-backup.db
│   └── pfmis-auth-latest-daily-backup.db.sha256
└── users/
    └── <user-id>/
        ├── pfmis.db
        └── backups/
```

The central `pfmis-auth.db` contains identities and authentication events. Financial data remains in each user's own `pfmis.db`.

## Security controls

- PBKDF2-HMAC-SHA-256 password hashing
- Random salt per password
- 210,000 PBKDF2 iterations
- Constant-time password comparison
- Password complexity validation
- Fifteen-minute lockout after five failed attempts
- Active/inactive account control
- Final-active-Super-Administrator protection
- Super-Administrator checks in the service/database layer, not only in the user interface
- Per-user AI credential preference keys
- Per-user AI settings, logs and backup files
- Session, navigation and refresh-listener reset on logout or workspace change

## Existing system upgrade

When the first Super Administrator is created, PFMIS looks for the existing single-user `pfmis.db`. When found, it copies that database into the first administrator's private workspace so existing records are retained.

## Server deployment foundation

`docs/postgres/PFMIS_multi_user_security_2026_07_22.sql` provides a PostgreSQL ownership and Row-Level Security design for a future server/API edition. In server mode, every user-owned row carries `owner_user_id`; normal users can access only their rows, while the Super Administrator policy can access all rows.

The desktop client must not connect directly to the server database. A trusted backend must authenticate the user and set the database session identity.

## Validation performed

- All non-JavaFX core source files compiled with JDK 21.
- Authentication UI controllers and `MainApp` passed a syntax/type compile against JavaFX API stubs.
- All 32 FXML files parsed successfully.
- All FXML action methods and `fx:id` controller bindings were matched.
- Password hashing and verification smoke tests passed.
- Normal-user workspace switching was rejected; Super Administrator switching passed.
- Separate users resolved to separate database paths.
- Authentication schema constraints and case-insensitive username uniqueness were checked with SQLite.

A full Maven/OpenJFX runtime build was not executed in this environment because Maven/OpenJFX dependencies were unavailable locally.
