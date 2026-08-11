# PFMIS Multi-User Security and Workspace Guide

## Implemented behaviour

PFMIS now starts at a sign-in screen instead of opening financial records directly.

- The first **Super Administrator** is provisioned automatically from the local ignored `.env` when no active Super Administrator exists.
- Public self-registration is disabled; additional accounts must be created by a Super Administrator from **Manage Users**.
- Every additional account created by a Super Administrator receives a separate private PFMIS workspace.
- A normal user can open only his or her own workspace.
- The Super Administrator can view the registered-user list and open any active user's workspace.
- The active workspace is displayed clearly in the application header.
- Signing out clears the authenticated session, workspace selection, navigation state, and controller refresh listeners.

## Data separation

The desktop edition uses physical database separation:

```text
PFMIS application data/
├── pfmis-auth.db
├── security-backups/
└── users/
    ├── 1/
    │   ├── pfmis.db
    │   └── backups/
    ├── 2/
    │   ├── pfmis.db
    │   └── backups/
    └── ...
```

`pfmis-auth.db` stores only user authentication and access-audit information. Each user's financial records, categories, accounts, transactions, budgets, projects, goals, contacts, reports, AI settings, AI logs, system logs, and backups remain inside that user's own `pfmis.db`.

The database path is selected from the authenticated workspace. It is not taken from a username or user-supplied file path. When the Super Administrator is in his or her own workspace, the automatic backup service also creates a daily backup of the central user registry.

## Authentication controls

Passwords are not stored as plain text. PFMIS uses:

- PBKDF2 with HMAC-SHA-256;
- a random salt per password;
- 600,000 iterations;
- constant-time hash comparison;
- a minimum password policy of eight characters, uppercase, lowercase, and a number;
- temporary lockout after five failed login attempts;
- active/inactive user status;
- protection against deactivating the final active Super Administrator.

Users can change their own password from **My Account**. The Super Administrator can reset another user's password.

## Super Administrator functions

The **Manage Users** screen allows the Super Administrator to:

- create a normal user or another Super Administrator;
- activate or deactivate an account;
- reset a user's password;
- open an active user's workspace;
- return to the administrator's own workspace.

Workspace access is recorded in the authentication log.

## Existing-data migration

When PFMIS is upgraded from a single-user version and the first Super Administrator is provisioned, an existing `pfmis.db` is copied automatically into that administrator's new workspace. New users start with separate empty databases and their own default categories and AI settings.

## Server deployment

The desktop implementation uses one SQLite database per user for strong local separation. A server edition should not trust the desktop client to enforce access. The server must enforce ownership in the API and database.

The accompanying PostgreSQL script demonstrates a server-ready model using:

- authenticated users;
- `owner_user_id` on user-owned records;
- role checks;
- PostgreSQL Row-Level Security;
- Super Administrator bypass policies;
- session variables set by the authenticated backend.

The server backend should set these values only after validating a signed-in session:

```sql
SET LOCAL pfmis.user_id = '123';
SET LOCAL pfmis.is_super_admin = 'false';
```

Normal clients must never be allowed to set these values directly on a database connection.
