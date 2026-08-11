# PFMIS Backup and Recovery

PFMIS stores user finance data locally. Backups protect user data but must also be handled as sensitive financial records.

## Backup Rules

- Backups must not be included in source control.
- Backups must not be included in release archives.
- Backup files should inherit restrictive file permissions.
- Encrypted backup archives should be preferred where feasible.
- Restore operations should verify SQLite integrity before replacing active data.

## Recovery Center Wording

The current local status and backup workflow should be described as local data and recovery. It should not be presented as server synchronization unless a real remote sync architecture exists.

The UI should show:

- local database status
- latest backup status
- integrity-check results
- restore options
- backup location
- pending maintenance actions

## Current Limitations

- Full encrypted backup support is not complete.
- Restore integrity tests are still pending.
- The Sync Center naming and behavior still need the planned correction.
