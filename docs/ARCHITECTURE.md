# PFMIS Architecture

PFMIS is currently a local JavaFX desktop application backed by SQLite files. It is a local multi-account workspace system, not a networked concurrent multi-user platform.

## Current Runtime Shape

- JavaFX controllers load FXML screens from `src/main/resources`.
- SQLite is used for local finance and authentication data.
- `DatabaseHandler` still contains too much schema, SQL, migration, and business behavior.
- Local AI can run through a bundled `llama.cpp` runtime when the model and executable are present.
- External AI providers are optional and must be treated as a privacy-sensitive feature.

## Target Layers

Future refactoring should move code incrementally into:

- `domain`
- `model`
- `repository`
- `service`
- `controller`
- `infrastructure`
- `security`
- `migration`
- `reporting`
- `ai`

Controllers should only validate UI input, call services, and update the UI. Repositories should own SQL. Services should own transactions and business rules.

## Dependency Direction

The intended dependency direction is:

`controller -> service -> repository -> infrastructure`

Domain objects should not depend on JavaFX, JDBC, or controller classes.

## Future Central Deployment

A central deployment requires a real server architecture:

`desktop or web client -> HTTPS REST API -> server-side authentication and authorisation -> PostgreSQL`

The desktop client must not connect directly to a remote PostgreSQL database with embedded credentials.

## Current Limitations

- The full repository/service split is not complete.
- Monetary migration is not complete across all modules.
- SQLite database encryption is not implemented.
- Sync Center behavior should be renamed as local recovery unless a real server sync layer is added.
