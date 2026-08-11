# Remember Me DPAPI Verification

Use a test account and a disposable PFMIS data directory when possible.

1. Start PFMIS with at least one registered administrator account.
2. Enter a valid username or email and password, select `Remember this login`, then sign in.
3. Sign out or close PFMIS.
4. Reopen PFMIS and confirm the username/email and password fields are populated, `Remember this login` is selected, the password is masked, and sign-in does not happen automatically.
5. Click `Sign In` and confirm the restored credentials authenticate successfully.
6. Return to the login page, uncheck `Remember this login`, and confirm `Saved login credentials removed.` appears, the password field is cleared, and `Forget saved login` is hidden.
7. Save credentials again, then use the in-app password change flow to change that account password. Reopen the login page and try the restored old password. Confirm it is rejected, saved credentials are removed, and the user can enter the new password manually.
8. Save credentials again, then disable or delete that account with a Super Administrator account. Reopen the login page and try the restored credentials. Confirm sign-in fails and the saved credentials are removed.
9. Save credentials again, then corrupt the stored `rememberedPassword` Java Preferences value for the PFMIS login node. Reopen PFMIS and confirm `The saved password could not be restored. Please enter it again.` appears and saved credentials are cleared.
10. Run on an operating system where Windows DPAPI is unavailable. Select `Remember this login`, sign in successfully, then reopen PFMIS. Confirm only the username/email is remembered, the password is blank, and manual login still works.
11. Start with no registered users. Confirm Remember Me is disabled until the first Super Administrator account is created.

The stored password must not appear in SQLite tables, plaintext files, `.env`, logs, audit-log details, source code, or Java Preferences as plaintext.
