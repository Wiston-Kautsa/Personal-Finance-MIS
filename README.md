# PFMIS

Personal Finance Management Information System built with Java 21, JavaFX and SQLite.

## Multi-user start-up

Run PFMIS and create the first account. The first account becomes the **Super Administrator**. Every user created by the Super Administrator receives a separate private financial workspace and separate SQLite database.

Normal users can access only their own accounts, transactions, budgets, projects, goals, contacts, reports, AI settings and backups. The Super Administrator can manage users and open any active user's workspace from **Manage Users**.

## Run during development

```bash
mvn clean javafx:run
```

The application stores system data in the operating-system application-data directory rather than the project folder.

```text
PFMIS/
├── pfmis-auth.db
└── users/<user-id>/pfmis.db
```

See `docs/PFMIS-Multi-User-Security-and-Workspace-Guide.md` for the complete security and server-deployment design.
