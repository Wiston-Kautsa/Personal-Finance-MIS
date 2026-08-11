# PFMIS Security Notes

## Credentials

PFMIS must not include real credentials in source control or release archives. The following are blocked from release packaging:

- `.env` files other than `.env.example`
- SQLite databases and journals
- lock files
- logs
- backups
- exports and generated reports
- local AI model files
- archives nested inside the release
- real private email addresses, passwords, API keys or access tokens

`.env.example` contains placeholders only. Do not add real Super Administrator passwords, SMTP/App Passwords, API keys, or tokens to committed files. A clean installation provisions the first Super Administrator automatically from the local ignored `.env`; the password is stored only as a secure hash in the authentication database.

## Rotation Required

Any Gmail App Password, SMTP/IMAP password or external AI API key that was previously present in a project file, release archive, screenshot or shared workspace must be revoked and replaced at the provider. Removing it from the repository is not enough.

## Local Multi-Account Workspace Separation

PFMIS is a local desktop application. It is not a central concurrent multi-user system. Authentication data is separate from each user's finance database. Normal users must not be able to open another user's workspace. Super Administrator workspace access must be audited.

Future central deployment should use this architecture:

```text
desktop or web client
    -> HTTPS REST API
    -> server-side authentication and authorization
    -> PostgreSQL database
```

The desktop client must not connect directly to remote PostgreSQL using embedded database credentials.

## Current Security Limitations

- External AI API key storage still needs operating-system credential manager integration on every supported platform.
- Password reset currently sends a temporary password through the configured System Email. A one-time token or code flow with token hashing, expiry, rate limiting and remembered-login invalidation is still recommended for a later hardening phase.
- Financial databases and backups are not yet SQLCipher-encrypted by default.
- Session timeout and screen-lock enforcement needs a full application-level implementation pass.

These limitations should be fixed in later controlled phases after packaging and baseline tests remain green.
