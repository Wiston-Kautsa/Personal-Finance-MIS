package com.wk.pfmis.db;

import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.BudgetProgress;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanInstallmentRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanPaymentCommand;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRecord;
import com.wk.pfmis.db.DatabaseHandler.CentralLoanRegistrationCommand;
import com.wk.pfmis.db.DatabaseHandler.CommunitySavingsGroupCommand;
import com.wk.pfmis.db.DatabaseHandler.CommunitySavingsGroupSummary;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupContributionCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupPayoutCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupProfileCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupTransactionRecord;
import com.wk.pfmis.db.DatabaseHandler.TransferFxMetadata;
import com.wk.pfmis.fx.ExchangeRateQuote;
import com.wk.pfmis.fx.ExchangeRateSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DatabaseHandlerIntegrationTest {
    @TempDir
    static Path dataRoot;

    private static DatabaseHandler database;

    @BeforeAll
    static void initializeWorkspace() throws Exception {
        System.setProperty("pfmis.data.dir", dataRoot.toString());
        Path legacyDatabase = dataRoot.resolve("users").resolve("1").resolve("pfmis.db");
        Files.createDirectories(legacyDatabase.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + legacyDatabase);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_name TEXT NOT NULL,
                        account_type TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE transactions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_id INTEGER NOT NULL,
                        transaction_type TEXT NOT NULL,
                        transaction_purpose TEXT DEFAULT 'NORMAL',
                        transaction_status TEXT DEFAULT 'COMPLETED',
                        amount REAL NOT NULL,
                        transaction_date TEXT NOT NULL,
                        description TEXT,
                        posting_status TEXT DEFAULT 'POSTED',
                        settlement_status TEXT DEFAULT 'NOT_APPLICABLE'
                    )
                    """);
            statement.execute("""
                    CREATE TRIGGER legacy_transaction_guard_before_insert
                    BEFORE INSERT ON transactions
                    BEGIN
                        SELECT CASE
                            WHEN NEW.transaction_type NOT IN ('INCOME', 'EXPENSE', 'TRANSFER')
                            THEN RAISE(ABORT, 'Invalid transaction type')
                        END;
                        SELECT CASE
                            WHEN COALESCE(NEW.transaction_purpose, 'NORMAL') NOT IN (
                                'NORMAL', 'PROJECT_EXPENSE', 'MONEY_LENT', 'MONEY_BORROWED',
                                'LENT_REPAID', 'BORROWED_REPAID', 'SUPPORT_GIVEN', 'SAVINGS',
                                'GOAL_CONTRIBUTION', 'TRANSFER_IN', 'TRANSFER_OUT'
                            )
                            THEN RAISE(ABORT, 'Invalid transaction purpose')
                        END;
                        SELECT CASE
                            WHEN COALESCE(NEW.transaction_status, 'COMPLETED') NOT IN (
                                'COMPLETED', 'OPEN', 'PARTIALLY_CLEARED', 'CLEARED', 'CANCELLED'
                            )
                            THEN RAISE(ABORT, 'Invalid legacy transaction status')
                        END;
                        SELECT CASE
                            WHEN COALESCE(NEW.posting_status, 'POSTED') NOT IN ('POSTED', 'DRAFT')
                            THEN RAISE(ABORT, 'Invalid posting status')
                        END;
                    END
                    """);
            statement.execute("""
                    CREATE TRIGGER legacy_transaction_guard_before_update
                    BEFORE UPDATE ON transactions
                    BEGIN
                        SELECT CASE
                            WHEN NEW.transaction_type NOT IN ('INCOME', 'EXPENSE', 'TRANSFER')
                            THEN RAISE(ABORT, 'Invalid transaction type')
                        END;
                        SELECT CASE
                            WHEN COALESCE(NEW.transaction_purpose, 'NORMAL') NOT IN (
                                'NORMAL', 'PROJECT_EXPENSE', 'MONEY_LENT', 'MONEY_BORROWED',
                                'LENT_REPAID', 'BORROWED_REPAID', 'SUPPORT_GIVEN', 'SAVINGS',
                                'GOAL_CONTRIBUTION', 'TRANSFER_IN', 'TRANSFER_OUT'
                            )
                            THEN RAISE(ABORT, 'Invalid transaction purpose')
                        END;
                        SELECT CASE
                            WHEN COALESCE(NEW.transaction_status, 'COMPLETED') NOT IN (
                                'COMPLETED', 'OPEN', 'PARTIALLY_CLEARED', 'CLEARED', 'CANCELLED'
                            )
                            THEN RAISE(ABORT, 'Invalid legacy transaction status')
                        END;
                        SELECT CASE
                            WHEN COALESCE(NEW.settlement_status, 'NOT_APPLICABLE') NOT IN ('NOT_APPLICABLE', 'OPEN', 'SETTLED')
                            THEN RAISE(ABORT, 'Invalid settlement status')
                        END;
                    END
                    """);
        }
        UserSession.login(new SystemUser(
                1,
                "test-admin",
                "Test Administrator",
                "admin@example.invalid",
                SystemUser.ROLE_SUPER_ADMIN,
                SystemUser.STATUS_ACTIVE,
                LocalDate.now().toString(),
                ""
        ));
        database = DatabaseHandler.getInstance();
        database.initializeDatabase();
    }

    @AfterAll
    static void clearSession() {
        UserSession.clear();
        System.clearProperty("pfmis.data.dir");
    }

    @Test
    @Order(1)
    void olderSchemaUpgradesAndHealthCheckPasses() throws Exception {
        assertEquals("Database health check passed.", database.validateDatabaseHealth());
        assertTrue(columnExists("accounts", "is_deleted"));
        assertTrue(columnExists("accounts", "is_system_account"));
        assertTrue(columnExists("accounts", "status"));
        assertTrue(columnExists("accounts", "created_at"));
        assertTrue(migrationHistoryExists("workspace-currency-registry"));
        assertTrue(migrationHistoryExists("report-input-tables"));
        assertTrue(migrationHistoryExists("transaction-ledger-validation-v2"));
        assertTrue(migrationHistoryExists("automatic-fx-rates-v1"));
        assertTrue(columnExists("exchange_rates", "base_currency"));
        assertTrue(columnExists("exchange_rates", "quote_currency"));
        assertTrue(columnExists("exchange_rates", "rate"));
        assertTrue(columnExists("exchange_rates", "provider_name"));
        assertTrue(columnExists("exchange_rates", "is_manual"));
        assertTrue(columnExists("transactions", "exchange_rate"));
        assertTrue(columnExists("transactions", "converted_amount"));
        assertTrue(columnExists("transactions", "exchange_rate_source"));
        assertFalse(triggerExists("legacy_transaction_guard_before_insert"));
        assertFalse(triggerExists("legacy_transaction_guard_before_update"));
        assertCanonicalTransactionTrigger("trg_transactions_validate_insert");
        assertCanonicalTransactionTrigger("trg_transactions_validate_update");
    }

    @Test
    @Order(2)
    void legacyTriggerMigrationAllowsOpeningBalanceAuditExactlyOnce() throws Exception {
        int accountId = database.addAccount(
                "Current Account",
                "Bank Account",
                "MWK",
                "National Bank",
                "1001000817",
                1000.50,
                LocalDate.now().toString(),
                0,
                "Salary",
                "City Center",
                "ACTIVE",
                "test"
        );

        Account account = accountById(accountId);
        assertEquals("Current Account", account.getAccountName());
        assertEquals("National Bank", account.getBankProviderName());
        assertEquals(1000.50, account.getCurrentBalance(), 0.005);
        assertEquals(1, rowCount("accounts"));
        assertEquals(1, openingBalanceAuditCount(accountId));

        database.initializeDatabase();

        assertEquals(1, accountRowsByName("Current Account"));
        assertEquals(1, openingBalanceAuditCount(accountId));
        assertEquals(1000.50, accountById(accountId).getCurrentBalance(), 0.005);

        int zeroAccountId = database.addAccount(
                "Cash Box",
                "Cash",
                "MWK",
                "Cash",
                "",
                0,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        );

        Account zeroAccount = accountById(zeroAccountId);
        assertEquals("Cash Box", zeroAccount.getAccountName());
        assertEquals(0, zeroAccount.getCurrentBalance(), 0.005);
        assertEquals(0, openingBalanceAuditCount(zeroAccountId));
    }

    @Test
    @Order(3)
    void createsBankAccountWithOpeningBalanceAndListsIt() {
        int accountId = database.addAccount(
                "Main Bank",
                "Bank Account",
                "MWK",
                "NBS Bank",
                "123456789",
                1000.50,
                LocalDate.now().toString(),
                0,
                "Salary",
                "Blantyre",
                "ACTIVE",
                "test"
        );

        Account account = accountById(accountId);
        assertEquals("NBS Bank", account.getBankProviderName());
        assertEquals(1000.50, account.getCurrentBalance(), 0.005);
    }

    @Test
    @Order(4)
    void migratedLegacyTriggersAcceptDedicatedLedgerTypes() throws Exception {
        int accountId = database.addAccount(
                "Dedicated Ledger Trigger Account",
                "Cash",
                "MWK",
                "Cash",
                "",
                0,
                LocalDate.now().toString(),
                0,
                "Trigger acceptance",
                "",
                "ACTIVE",
                "test"
        );

        insertLedgerTransaction(accountId, "LOAN", "MONEY_BORROWED", "OPEN", 10);
        insertLedgerTransaction(accountId, "TRANSFER", "TRANSFER_IN", "COMPLETED", 20);
        insertLedgerTransaction(accountId, "ASSET_SALE", "ASSET_SALE_PROCEEDS", "COMPLETED", 30);
        insertLedgerTransaction(accountId, "ADJUSTMENT", "BALANCE_DECREASE", "COMPLETED", 5);

        assertEquals(4, transactionRowsForAccount(accountId));
        assertEquals(55, accountById(accountId).getCurrentBalance(), 0.005);
    }

    @Test
    @Order(4)
    void bankAccountRequiresProviderData() {
        assertThrows(IllegalArgumentException.class, () -> database.addAccount(
                "Bank Without Provider",
                "Bank Account",
                "MWK",
                "",
                "555000",
                0,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        ));
        assertFalse(database.accountIdentityExists(null, "Bank Without Provider", "Bank Account", "", "555000"));
    }

    @Test
    @Order(5)
    void retryAfterSuccessfulInsertDoesNotCreateDuplicateAccount() {
        int firstId = database.addAccount(
                "Retry Guard Account",
                "Cash",
                "MWK",
                "Cash",
                "",
                0,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        );

        assertThrows(IllegalArgumentException.class, () -> database.addAccount(
                "Retry Guard Account",
                "Cash",
                "MWK",
                "Cash",
                "",
                0,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        ));

        long matches = database.listAccounts().stream()
                .filter(account -> "Retry Guard Account".equals(account.getAccountName()))
                .count();
        assertEquals(1, matches);
        assertEquals(firstId, accountById(firstId).getId());
    }

    @Test
    @Order(6)
    void invalidAccountInputDoesNotWritePartialData() {
        assertThrows(IllegalArgumentException.class, () -> database.addAccount(
                "Invalid Negative Opening",
                "Cash",
                "MWK",
                "Cash",
                "",
                -1,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        ));

        assertFalse(database.accountIdentityExists(null, "Invalid Negative Opening", "Cash", "Cash", ""));
    }

    @Test
    @Order(7)
    void closingNonZeroBalanceAccountIsRejected() {
        int accountId = database.addAccount(
                "Non Zero Close Guard",
                "Cash",
                "MWK",
                "Cash",
                "",
                50,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        );

        assertThrows(IllegalArgumentException.class, () -> database.updateAccountLifecycleStatus(accountId, "CLOSED"));
        assertEquals("ACTIVE", accountById(accountId).getStatus());
    }

    @Test
    @Order(8)
    void closingZeroBalanceReconciledAccountSucceeds() {
        int accountId = database.addAccount(
                "Zero Close Allowed",
                "Cash",
                "MWK",
                "Cash",
                "",
                0,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        );
        database.saveAccountReconciliation(null, accountId, LocalDate.now().toString(), 0, "matched");

        database.updateAccountLifecycleStatus(accountId, "CLOSED");

        assertEquals("CLOSED", accountById(accountId).getStatus());
    }

    @Test
    @Order(9)
    void frozenAccountRejectsOrdinaryPosting() {
        int accountId = database.addAccount(
                "Frozen Posting Guard",
                "Cash",
                "MWK",
                "Cash",
                "",
                0,
                LocalDate.now().toString(),
                0,
                "General use",
                "",
                "ACTIVE",
                "test"
        );
        database.updateAccountLifecycleStatus(accountId, "FROZEN");

        assertThrows(IllegalArgumentException.class, () -> database.recordTransaction(
                accountId,
                null,
                null,
                null,
                "INCOME",
                "NORMAL",
                "COMPLETED",
                10,
                LocalDate.now(),
                "should not post"
        ));
    }

    @Test
    @Order(10)
    void incomeExpenseAndTransferRemainBalanced() {
        int sourceId = database.addAccount("Atomic Source", "Cash", "MWK", "Cash", "", 1000, LocalDate.now().toString(), 0, "General use", "", "ACTIVE", "test");
        int destinationId = database.addAccount("Atomic Destination", "Cash", "MWK", "Cash", "", 0, LocalDate.now().toString(), 0, "General use", "", "ACTIVE", "test");

        database.recordTransaction(sourceId, null, null, null, "INCOME", "NORMAL", "COMPLETED", 200, LocalDate.now(), "income");
        database.recordTransaction(sourceId, null, null, null, "EXPENSE", "NORMAL", "COMPLETED", 125, LocalDate.now(), "expense");
        database.recordTransfer(sourceId, destinationId, 300, 300, LocalDate.now(), "transfer", "Cash", "T-1");

        assertEquals(775, accountById(sourceId).getCurrentBalance(), 0.005);
        assertEquals(300, accountById(destinationId).getCurrentBalance(), 0.005);
    }

    @Test
    @Order(11)
    void failedTransferRollsBackCompletely() {
        int sourceId = database.addAccount("Rollback Source", "Cash", "MWK", "Cash", "", 20, LocalDate.now().toString(), 0, "General use", "", "ACTIVE", "test");
        int destinationId = database.addAccount("Rollback Destination", "Cash", "MWK", "Cash", "", 0, LocalDate.now().toString(), 0, "General use", "", "ACTIVE", "test");

        assertThrows(IllegalArgumentException.class, () -> database.recordTransfer(sourceId, destinationId, 50, 50, LocalDate.now(), "too much", "Cash", "T-rollback"));

        assertEquals(20, accountById(sourceId).getCurrentBalance(), 0.005);
        assertEquals(0, accountById(destinationId).getCurrentBalance(), 0.005);
    }

    @Test
    @Order(12)
    void databaseInitializationIsIdempotentAndForeignKeysRemainValid() throws Exception {
        database.initializeDatabase();
        assertEquals("Database health check passed.", database.validateDatabaseHealth());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA foreign_key_check")) {
            assertFalse(resultSet.next());
        }
    }

    @Test
    @Order(13)
    void normalAccountCreationCreatesUserAccountOnly() {
        int accountId = database.addAccount("Visible User Account", "Cash", "MWK", "Cash", "", 0,
                LocalDate.now().toString(), 0, "General use", "", "ACTIVE", "test");

        Account account = accountById(accountId);
        assertFalse(account.isSystemAccount());
        assertTrue(database.listAccounts().stream().anyMatch(row -> row.getId() == accountId));
    }

    @Test
    @Order(14)
    void savingsGroupCreatesInternalLedgerExcludedFromAccounts() {
        int sourceId = database.addAccount("Savings Group Source", "Cash", "MWK", "Cash", "", 500,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");
        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null, null, "Titukuke Bank Nkhonde", "Titukuke", "Bank Nkhonde", "MWK", "",
                "Monthly", 50, "15", LocalDate.now(), LocalDate.now().plusMonths(11),
                LocalDate.now().plusMonths(12), 600, sourceId, "", "", "ACTIVE", "test"
        ));

        var profile = database.getSavingsGroupProfile(profileId);
        Account ledger = database.getInternalAccountById(profile.accountId());
        assertTrue(ledger.isSystemAccount());
        assertEquals("COMMUNITY_SAVINGS_INTERNAL", ledger.getAccountType());
        assertFalse(database.listAccounts().stream().anyMatch(account -> account.getId() == ledger.getId()));
    }

    @Test
    @Order(15)
    void communitySavingsHistoricalOpeningBalanceUsesAuditRowWithoutDoubleCounting() throws Exception {
        int groupId = database.createCommunitySavingsGroup(new CommunitySavingsGroupCommand(
                "Historical Bank Nkhonde",
                "Bank Nkhonde",
                "Existing group registered during trigger migration regression",
                "MWK",
                LocalDate.now().minusMonths(3),
                LocalDate.now().plusMonths(9),
                "Monthly",
                100,
                "",
                "",
                "",
                "Monthly",
                "15",
                "",
                0,
                "",
                "Historical opening balance test",
                750.25,
                true,
                LocalDate.now().minusMonths(3),
                LocalDate.now().minusMonths(3),
                0,
                0,
                0,
                0,
                0,
                0,
                LocalDate.now(),
                "Opening balance carried from legacy group records.",
                0,
                0,
                0,
                "",
                0,
                "Cash"
        ));

        CommunitySavingsGroupSummary group = database.listCommunitySavingsGroups().stream()
                .filter(row -> row.id() == groupId)
                .findFirst()
                .orElseThrow();
        Account ledger = database.getInternalAccountById(group.linkedAccountId());
        assertEquals(750.25, ledger.getCurrentBalance(), 0.005);
        assertEquals(1, openingBalanceAuditCount(ledger.getId()));
    }

    @Test
    @Order(15)
    void internalLedgerCannotBeManagedThroughOrdinaryAccountApis() {
        int sourceId = database.addAccount("Protected Ledger Source", "Cash", "MWK", "Cash", "", 100,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");
        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null, null, "Protected Chipeleganyu", "Protected", "Chipeleganyu", "MWK", "",
                "Monthly", 25, "1", LocalDate.now(), LocalDate.now().plusMonths(2),
                LocalDate.now().plusMonths(3), 75, sourceId, "", "", "ACTIVE", "test"
        ));
        int ledgerId = database.getSavingsGroupProfile(profileId).accountId();

        assertThrows(IllegalArgumentException.class, () -> database.updateAccount(
                ledgerId, "Edited Ledger", "Cash", "MWK", "Cash", "", 0, LocalDate.now().toString(),
                0, "Bad edit", "", "ACTIVE", "test"
        ));
        assertThrows(IllegalArgumentException.class, () -> database.updateAccountLifecycleStatus(ledgerId, "FROZEN"));
        assertThrows(IllegalArgumentException.class, () -> database.recordTransfer(sourceId, ledgerId, 10, 10,
                LocalDate.now(), "ordinary transfer to internal ledger", "Cash", "bad-internal"));
    }

    @Test
    @Order(16)
    void legacySavingsGroupAccountMigrationMarksLedgerAndPreservesTransactions() throws Exception {
        int legacyAccountId;
        int legacyTransactionId;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO accounts (
                        account_name, account_type, currency, bank_provider_name, account_number,
                        opening_balance, opening_balance_date, minimum_balance, account_purpose, branch_name,
                        account_category, account_subtype, community_group_id, is_system_account, status, notes, updated_at
                    ) VALUES ('Legacy Visible Bank Nkhonde', 'Community Savings', 'MWK', 'Legacy Group', '',
                        0, date('now', 'localtime'), 0, 'legacy savings profile', '', 'ASSET', 'BANK_NKHONDE',
                        NULL, 0, 'ACTIVE', 'legacy', CURRENT_TIMESTAMP)
                    """);
            try (ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
                resultSet.next();
                legacyAccountId = resultSet.getInt(1);
            }
            statement.executeUpdate("""
                    INSERT INTO community_savings_profiles (
                        workspace_id, account_id, group_name, group_type, contribution_frequency,
                        expected_contribution_amount, actual_start_date, status, notes, updated_at
                    ) VALUES (1, %d, 'Legacy Group', 'Bank Nkhonde', 'Monthly', 100,
                        date('now', 'localtime'), 'ACTIVE', 'legacy profile', CURRENT_TIMESTAMP)
                    """.formatted(legacyAccountId));
            statement.executeUpdate("""
                    INSERT INTO transactions (
                        account_id, transaction_type, transaction_purpose, transaction_status,
                        amount, transaction_date, description, source
                    ) VALUES (%d, 'TRANSFER', 'TRANSFER_IN', 'COMPLETED', 100,
                        date('now', 'localtime'), 'legacy contribution', 'TEST')
                    """.formatted(legacyAccountId));
            try (ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
                resultSet.next();
                legacyTransactionId = resultSet.getInt(1);
            }
        }

        database.initializeDatabase();

        Account ledger = database.getInternalAccountById(legacyAccountId);
        assertTrue(ledger.isSystemAccount());
        assertFalse(database.listAccounts().stream().anyMatch(account -> account.getId() == legacyAccountId));
        assertTrue(database.listTransactionsForAccount(legacyAccountId).stream()
                .anyMatch(transaction -> transaction.getId() == legacyTransactionId));
        database.initializeDatabase();
        assertEquals(legacyAccountId, database.getInternalAccountById(legacyAccountId).getId());
    }

    @Test
    @Order(17)
    void savingsContributionDebitsSourceCreditsLedgerAndRecordsHistory() {
        int sourceId = database.addAccount("Contribution Source", "Cash", "MWK", "Cash", "", 500,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");
        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null, null, "Contribution Bank Nkhonde", "Contribution Group", "Bank Nkhonde", "MWK", "",
                "Monthly", 50, "10", LocalDate.now(), LocalDate.now().plusMonths(5),
                LocalDate.now().plusMonths(6), 300, sourceId, "", "", "ACTIVE", "test"
        ));
        int ledgerId = database.getSavingsGroupProfile(profileId).accountId();
        double savingsBefore = database.getCommunitySavingsBalance();

        database.recordSavingsGroupContribution(new SavingsGroupContributionCommand(
                profileId, LocalDate.now(), "2026-08", 50, sourceId, "Cash", "SG-CON-1",
                "test contribution", "", false
        ));

        assertEquals(450, accountById(sourceId).getCurrentBalance(), 0.005);
        assertEquals(50, database.getInternalAccountById(ledgerId).getCurrentBalance(), 0.005);
        assertEquals(savingsBefore + 50, database.getCommunitySavingsBalance(), 0.005);
        List<SavingsGroupTransactionRecord> history = database.listSavingsGroupTransactions(profileId, 10);
        assertTrue(history.stream().anyMatch(row -> "CONTRIBUTION".equals(row.transactionClassification())
                && Math.abs(row.amount() - 50) < 0.005));
    }

    @Test
    @Order(18)
    void failedSavingsContributionRollsBackCompletely() {
        int sourceId = database.addAccount("Contribution Rollback Source", "Cash", "MWK", "Cash", "", 5,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");
        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null, null, "Contribution Rollback Group", "Rollback Group", "Bank Nkhonde", "MWK", "",
                "Monthly", 10, "10", LocalDate.now(), LocalDate.now().plusMonths(5),
                LocalDate.now().plusMonths(6), 60, sourceId, "", "", "ACTIVE", "test"
        ));
        int ledgerId = database.getSavingsGroupProfile(profileId).accountId();

        assertThrows(IllegalArgumentException.class, () -> database.recordSavingsGroupContribution(new SavingsGroupContributionCommand(
                profileId, LocalDate.now(), "2026-09", 10, sourceId, "Cash", "SG-CON-ROLLBACK",
                "too much", "", false
        )));

        assertEquals(5, accountById(sourceId).getCurrentBalance(), 0.005);
        assertEquals(0, database.getInternalAccountById(ledgerId).getCurrentBalance(), 0.005);
        assertTrue(database.listSavingsGroupTransactions(profileId, 10).isEmpty());
    }

    @Test
    @Order(19)
    void savingsPayoutDebitsLedgerCreditsReceivingAccountAndRecordsHistory() {
        int sourceId = database.addAccount("Payout Source", "Cash", "MWK", "Cash", "", 300,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");
        int receivingId = database.addAccount("Payout Receiving", "Cash", "MWK", "Cash", "", 0,
                LocalDate.now().toString(), 0, "Receiving", "", "ACTIVE", "test");
        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null, null, "Payout Chipeleganyu", "Payout Group", "Chipeleganyu", "MWK", "",
                "Monthly", 75, "10", LocalDate.now(), LocalDate.now().plusMonths(3),
                LocalDate.now().plusMonths(4), 300, sourceId, "", "", "ACTIVE", "test"
        ));
        int ledgerId = database.getSavingsGroupProfile(profileId).accountId();
        database.recordSavingsGroupContribution(new SavingsGroupContributionCommand(
                profileId, LocalDate.now(), "2026-10", 75, sourceId, "Cash", "SG-PAYOUT-SEED",
                "seed", "", false
        ));
        double savingsBeforePayout = database.getCommunitySavingsBalance();

        database.recordSavingsGroupPayout(new SavingsGroupPayoutCommand(
                profileId, LocalDate.now(), 75, 0, 0, 0, receivingId, "SG-PAY-1",
                "share-out", "", false
        ));

        assertEquals(0, database.getInternalAccountById(ledgerId).getCurrentBalance(), 0.005);
        assertEquals(75, accountById(receivingId).getCurrentBalance(), 0.005);
        assertEquals(savingsBeforePayout - 75, database.getCommunitySavingsBalance(), 0.005);
        assertTrue(database.listSavingsGroupTransactions(profileId, 10).stream()
                .anyMatch(row -> "ORIGINAL_SAVINGS_RETURN".equals(row.transactionClassification())
                        && Math.abs(row.amount() - 75) < 0.005));
    }

    @Test
    @Order(20)
    void availableAndSavingsTotalsAreSeparatedWithoutDoubleCounting() {
        int sourceId = database.addAccount("Dashboard Separation Source", "Cash", "MWK", "Cash", "", 500,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");
        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null, null, "Dashboard Separation Group", "Dashboard Group", "Bank Nkhonde", "MWK", "",
                "Monthly", 50, "10", LocalDate.now(), LocalDate.now().plusMonths(5),
                LocalDate.now().plusMonths(6), 300, sourceId, "", "", "ACTIVE", "test"
        ));
        double availableBefore = database.getAvailableCashAndBankBalance();
        double savingsBefore = database.getCommunitySavingsBalance();

        database.recordSavingsGroupContribution(new SavingsGroupContributionCommand(
                profileId, LocalDate.now(), "2026-11", 50, sourceId, "Cash", "SG-DASH-1",
                "dashboard separation", "", false
        ));

        assertEquals(availableBefore - 50, database.getAvailableCashAndBankBalance(), 0.005);
        assertEquals(savingsBefore + 50, database.getCommunitySavingsBalance(), 0.005);
        assertFalse(database.accountBalanceReport().stream()
                .anyMatch(row -> row.getLabel().equals("Dashboard Separation Group")));
    }

    @Test
    @Order(21)
    void displayCurrencyValueIsCanonicalizedWhenSavingsGroupIsSaved() {
        int sourceId = database.addAccount("Display Currency Source", "Cash", "MWK", "Cash", "", 200,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");

        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null, null, "Display Currency Group", "Display Currency", "Bank Nkhonde",
                "MWK - Malawian Kwacha", "", "Monthly", 20, "10",
                LocalDate.now(), LocalDate.now().plusMonths(2), LocalDate.now().plusMonths(3),
                60, sourceId, "", "", "ACTIVE", "test"
        ));

        var profile = database.getSavingsGroupProfile(profileId);
        assertEquals("MWK", profile.currency());
        assertEquals("MWK", database.getInternalAccountById(profile.accountId()).getCurrency());
    }

    @Test
    @Order(22)
    void invalidTwoLetterSavingsGroupCurrencyIsRejectedForPersistedRecords() {
        int sourceId = database.addAccount("Invalid Currency Source", "Cash", "MWK", "Cash", "", 200,
                LocalDate.now().toString(), 0, "Source", "", "ACTIVE", "test");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                        null, null, "Invalid Currency Group", "Invalid Currency", "Bank Nkhonde",
                        "MK", "", "Monthly", 20, "10",
                        LocalDate.now(), LocalDate.now().plusMonths(2), LocalDate.now().plusMonths(3),
                        60, sourceId, "", "", "ACTIVE", "test"
                )));
        assertTrue(exception.getMessage().contains("three-letter ISO"));
    }

    @Test
    @Order(23)
    void migrationCanonicalizesLegacyDisplayCurrencyOnSavingsGroupLedgers() throws Exception {
        int legacyAccountId;
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO accounts (
                        account_name, account_type, currency, bank_provider_name, account_number,
                        opening_balance, opening_balance_date, minimum_balance, account_purpose, branch_name,
                        account_category, account_subtype, community_group_id, is_system_account, status, notes, updated_at
                    ) VALUES ('Legacy Display Currency Group', 'Community Savings', 'MWK - Malawian Kwacha', 'Legacy Group', '',
                        0, date('now', 'localtime'), 0, 'legacy savings profile', '', 'ASSET', 'BANK_NKHONDE',
                        NULL, 0, 'ACTIVE', 'legacy', CURRENT_TIMESTAMP)
                    """);
            try (ResultSet resultSet = statement.executeQuery("SELECT last_insert_rowid()")) {
                resultSet.next();
                legacyAccountId = resultSet.getInt(1);
            }
            statement.executeUpdate("""
                    INSERT INTO community_savings_profiles (
                        workspace_id, account_id, group_name, group_type, contribution_frequency,
                        expected_contribution_amount, actual_start_date, status, notes, updated_at
                    ) VALUES (1, %d, 'Legacy Display Currency', 'Bank Nkhonde', 'Monthly', 100,
                        date('now', 'localtime'), 'ACTIVE', 'legacy profile', CURRENT_TIMESTAMP)
                    """.formatted(legacyAccountId));
        }

        database.initializeDatabase();

        assertEquals("MWK", database.getInternalAccountById(legacyAccountId).getCurrency());
        assertTrue(migrationHistoryExists("savings-groups-canonical-currencies"));
    }

    @Test
    @Order(24)
    void recordDisposalSearchSupportsCommunityShareOutLifecycleStatus() throws Exception {
        assertTrue(columnExists("community_share_outs", "status"));

        List<DatabaseHandler.RecordDisposalCandidateData> candidates = database.searchRecordDisposalCandidates(
                "Other",
                "",
                "All",
                null,
                null,
                "",
                false
        );

        assertTrue(candidates.stream().allMatch(candidate -> candidate.status() != null));
    }

    @Test
    @Order(25)
    void draftBudgetPlanDoesNotPostTransactionOrAbsorbMonthlyExpenses() throws Exception {
        String month = LocalDate.now().toString().substring(0, 7);
        int accountId = database.addAccount("Budget Draft Cash", "Cash", "MWK", "Cash", "", 500,
                LocalDate.now().toString(), 0, "Budget regression", "", "ACTIVE", "test");
        Category category = database.findOrCreateCategory("Budget Draft Food", "EXPENSE");

        database.recordTransaction(
                accountId,
                category.getId(),
                null,
                null,
                "EXPENSE",
                "NORMAL",
                "COMPLETED",
                75,
                LocalDate.now(),
                "Food spending behind a category allocation"
        );
        int transactionsAfterExpense = rowCount("transactions");

        database.addBudgetPlanDraft(
                "Draft Header Regression",
                month,
                "Monthly",
                LocalDate.now().withDayOfMonth(1).toString(),
                LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString(),
                "MWK",
                1000,
                100,
                400,
                "Draft plan header"
        );

        assertEquals(transactionsAfterExpense, rowCount("transactions"));
        List<BudgetProgress> headerOnly = database.listBudgetProgress(month).stream()
                .filter(row -> "Draft Header Regression".equals(row.getBudgetName()))
                .toList();
        assertEquals(1, headerOnly.size());
        assertNull(headerOnly.get(0).getCategoryId());
        assertEquals(0, headerOnly.get(0).getAmountLimit(), 0.005);
        assertEquals(0, headerOnly.get(0).getSpent(), 0.005);

        database.addBudget(
                "Draft Header Regression",
                category.getId(),
                month,
                200,
                false,
                "DRAFT",
                "Food limit",
                "Monthly",
                LocalDate.now().withDayOfMonth(1).toString(),
                LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth()).toString(),
                "MWK",
                1000,
                100,
                400
        );
        List<BudgetProgress> rows = database.listBudgetProgress(month).stream()
                .filter(row -> "Draft Header Regression".equals(row.getBudgetName()))
                .toList();

        BudgetProgress header = rows.stream()
                .filter(row -> row.getCategoryId() == null)
                .findFirst()
                .orElseThrow();
        BudgetProgress allocation = rows.stream()
                .filter(row -> Integer.valueOf(category.getId()).equals(row.getCategoryId()))
                .findFirst()
                .orElseThrow();
        assertEquals(0, header.getSpent(), 0.005);
        assertEquals(75, allocation.getSpent(), 0.005);
    }

    @Test
    @Order(26)
    void legacyWorkspaceWithoutSystemAccountColumnMigratesBeforeBudgetQueries() throws Exception {
        SystemUser legacyUser = new SystemUser(
                77,
                "legacy-user",
                "Legacy User",
                "legacy@example.invalid",
                SystemUser.ROLE_USER,
                SystemUser.STATUS_ACTIVE,
                LocalDate.now().toString(),
                ""
        );
        Path legacyDatabase = DatabaseHandler.userDatabasePath(legacyUser.getId());
        Files.createDirectories(legacyDatabase.getParent());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + legacyDatabase);
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        account_name TEXT NOT NULL,
                        account_type TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    INSERT INTO accounts (account_name, account_type)
                    VALUES ('Legacy Cash Account', 'Cash')
                    """);
            statement.execute("""
                    INSERT INTO accounts (account_name, account_type)
                    VALUES ('Legacy Savings Group Ledger', 'community savings')
                    """);
        }

        try {
            UserSession.switchWorkspace(legacyUser);
            database.initializeDatabase();
            database.initializeDatabase();

            assertTrue(columnExists("accounts", "is_system_account"));
            assertEquals(0, accountSystemFlag("Legacy Cash Account"));
            assertEquals(1, accountSystemFlag("Legacy Savings Group Ledger"));
            assertTrue(database.listAccounts().stream()
                    .anyMatch(account -> "Legacy Cash Account".equals(account.getAccountName())));
            assertFalse(database.listAccounts().stream()
                    .anyMatch(account -> "Legacy Savings Group Ledger".equals(account.getAccountName())));
            assertTrue(migrationHistoryExists("accounts-system-ledger-backfill-v7"));
            assertEquals("Database health check passed.", database.validateDatabaseHealth());
            try (var backups = Files.list(DatabaseHandler.defaultBackupDirectory())) {
                assertTrue(backups.anyMatch(path -> path.getFileName().toString().startsWith("pre-migration-")));
            }
        } finally {
            UserSession.returnToOwnWorkspace();
            database.initializeDatabase();
        }
    }

    @Test
    @Order(27)
    void centralBorrowedLoanPostsProceedsAsLoanNotIncomeAndGeneratesSchedule() {
        int accountId = database.addAccount("Central Loan Cash", "Cash", "MWK", "Cash", "", 1000,
                LocalDate.now().toString(), 0, "Central loan regression", "", "ACTIVE", "test");

        int loanId = database.registerBorrowedLoan(new CentralLoanRegistrationCommand(
                "Commercial borrowed loan",
                "Commercial Bank",
                "Test Commercial Bank",
                null,
                500,
                "MWK",
                LocalDate.of(2026, 8, 1),
                "Monthly",
                "Cash",
                "Fixed / Flat Interest Amount",
                0,
                50,
                0,
                5,
                LocalDate.of(2026, 8, 31),
                accountId,
                accountId,
                "Manual",
                null,
                null,
                null,
                null,
                "Active",
                "LN-PROCEEDS-REGRESSION",
                "central loan regression"
        ));

        CentralLoanRecord loan = database.getCentralLoan(loanId);
        assertEquals(500, loan.principalAmount(), 0.005);
        assertEquals(550, loan.totalRepayable(), 0.005);
        assertEquals(550, loan.outstandingBalance(), 0.005);
        assertEquals(1500, accountById(accountId).getCurrentBalance(), 0.005);

        List<CentralLoanInstallmentRecord> installments = database.listCentralLoanInstallments(loanId);
        assertEquals(5, installments.size());
        assertEquals(110, installments.get(0).totalDue(), 0.005);
        assertEquals(100, installments.get(0).principalDue(), 0.005);
        assertEquals(10, installments.get(0).interestDue(), 0.005);

        List<FinanceTransaction> proceeds = database.listTransactionHistory(new DatabaseHandler.TransactionHistoryFilter(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                accountId,
                "Loan Proceeds",
                "",
                null,
                "LN-PROCEEDS-REGRESSION",
                true,
                50,
                0
        )).transactions();
        assertEquals(1, proceeds.size());
        assertEquals("LOAN", proceeds.get(0).getTransactionType());
        assertEquals("LOAN_PROCEEDS", proceeds.get(0).getTransactionPurpose());
        assertEquals(loanId, proceeds.get(0).getLoanId());

        long matchingIncomeRows = database.listTransactionHistory(new DatabaseHandler.TransactionHistoryFilter(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                accountId,
                "Income",
                "",
                null,
                "LN-PROCEEDS-REGRESSION",
                true,
                50,
                0
        )).transactions().size();
        assertEquals(0, matchingIncomeRows);
    }

    @Test
    @Order(28)
    void centralManualRepaymentSplitsPrincipalInterestUpdatesBalancesAndBlocksDuplicatePosting() {
        int accountId = database.addAccount("Central Repayment Cash", "Cash", "MWK", "Cash", "", 1000,
                LocalDate.now().toString(), 0, "Central repayment regression", "", "ACTIVE", "test");
        int loanId = database.registerBorrowedLoan(new CentralLoanRegistrationCommand(
                "Commercial bank test loan",
                "Commercial Bank",
                "National Bank Test",
                null,
                1000,
                "MWK",
                LocalDate.of(2026, 8, 1),
                "Monthly",
                "Bank transfer",
                "Fixed / Flat Interest Amount",
                0,
                100,
                0,
                10,
                LocalDate.of(2026, 8, 30),
                accountId,
                accountId,
                "Manual",
                null,
                null,
                null,
                null,
                "Active",
                "LN-REPAYMENT-REGRESSION",
                "central repayment regression"
        ));

        int paymentId = database.recordCentralLoanPayment(new CentralLoanPaymentCommand(
                loanId,
                null,
                accountId,
                LocalDate.of(2026, 8, 15),
                60,
                "PAY-REGRESSION-1",
                "partial payment",
                false
        ));

        assertTrue(paymentId > 0);
        CentralLoanRecord loan = database.getCentralLoan(loanId);
        assertEquals(1040, loan.outstandingBalance(), 0.005);
        assertEquals(1940, accountById(accountId).getCurrentBalance(), 0.005);
        CentralLoanInstallmentRecord first = database.listCentralLoanInstallments(loanId).get(0);
        assertEquals("PARTIALLY_PAID", first.status());
        assertEquals(50, first.remainingDue(), 0.005);

        DatabaseHandler.CentralLoanPaymentRecord payment = database.listCentralLoanPayments(loanId).stream()
                .filter(row -> row.id() == paymentId)
                .findFirst()
                .orElseThrow();
        assertEquals(10, payment.interestPaid(), 0.005);
        assertEquals(50, payment.principalPaid(), 0.005);
        assertNotNull(payment.transactionId());

        assertThrows(IllegalArgumentException.class, () -> database.recordCentralLoanPayment(new CentralLoanPaymentCommand(
                loanId,
                null,
                accountId,
                LocalDate.of(2026, 8, 15),
                60,
                "PAY-REGRESSION-1",
                "duplicate partial payment",
                false
        )));
    }

    @Test
    @Order(29)
    void centralLoanCanLinkToRegisteredSavingsGroupWithoutDuplicatingGroup() {
        int sourceAccountId = database.addAccount("Linked Savings Source Cash", "Cash", "MWK", "Cash", "", 2000,
                LocalDate.now().toString(), 0, "Savings group loan source", "", "ACTIVE", "test");
        int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                null,
                null,
                "Linked Bank Nkhonde Ledger",
                "Chisomo Bank Nkhonde",
                "Bank Nkhonde",
                "MWK",
                "BN-001",
                "Monthly",
                100,
                "30",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                LocalDate.of(2026, 12, 31),
                600,
                sourceAccountId,
                "Treasurer",
                "0999000000",
                "ACTIVE",
                "Savings group loan source"
        ));

        int loanId = database.registerBorrowedLoan(new CentralLoanRegistrationCommand(
                "Bank Nkhonde linked loan",
                "Village Bank / Bank Nkhonde",
                "Chisomo Bank Nkhonde",
                profileId,
                400,
                "MWK",
                LocalDate.of(2026, 8, 5),
                "Monthly",
                "Cash",
                "Flat Percentage Interest",
                10,
                0,
                0,
                4,
                LocalDate.of(2026, 9, 5),
                sourceAccountId,
                sourceAccountId,
                "Manual",
                null,
                null,
                null,
                null,
                "Active",
                "LN-SG-LINK-REGRESSION",
                "linked to existing savings group"
        ));

        CentralLoanRecord loan = database.getCentralLoan(loanId);
        assertEquals(profileId, loan.savingsGroupId());
        assertEquals("Chisomo Bank Nkhonde", loan.savingsGroupName());
        assertEquals(1, database.listSavingsGroupProfiles().stream()
                .filter(profile -> "Chisomo Bank Nkhonde".equals(profile.groupName()))
                .count());
    }

    @Test
    @Order(30)
    void transactionHistoryCanRetrieveJulyRecordsFromAugustAndAllTransactions() {
        int accountId = database.addAccount("July History Cash", "Cash", "MWK", "Cash", "", 1000,
                LocalDate.of(2026, 7, 1).toString(), 0, "History regression", "", "ACTIVE", "test");
        Category category = database.findOrCreateCategory("July Regression Expenses", "EXPENSE");
        database.recordTransaction(accountId, category.getId(), null, null, "EXPENSE", "NORMAL", "COMPLETED",
                250, LocalDate.of(2026, 7, 31), "July rent regression", "Cash", "JULY-REGRESSION");
        database.recordTransaction(accountId, category.getId(), null, null, "EXPENSE", "NORMAL", "COMPLETED",
                75, LocalDate.of(2026, 8, 2), "August groceries regression", "Cash", "AUGUST-REGRESSION");

        DatabaseHandler.TransactionHistoryPage lastMonth = database.listTransactionHistory(new DatabaseHandler.TransactionHistoryFilter(
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                accountId,
                "Expense",
                "",
                null,
                "REGRESSION",
                true,
                50,
                0
        ));
        assertTrue(lastMonth.transactions().stream()
                .anyMatch(transaction -> "JULY-REGRESSION".equals(transaction.getReferenceNumber())));
        assertFalse(lastMonth.transactions().stream()
                .anyMatch(transaction -> "AUGUST-REGRESSION".equals(transaction.getReferenceNumber())));

        DatabaseHandler.TransactionHistoryPage all = database.listTransactionHistory(new DatabaseHandler.TransactionHistoryFilter(
                null,
                null,
                accountId,
                "Expense",
                "",
                null,
                "REGRESSION",
                true,
                50,
                0
        ));
        assertTrue(all.transactions().stream()
                .anyMatch(transaction -> "JULY-REGRESSION".equals(transaction.getReferenceNumber())));
        assertTrue(all.transactions().stream()
                .anyMatch(transaction -> "AUGUST-REGRESSION".equals(transaction.getReferenceNumber())));
        assertEquals(675, accountById(accountId).getCurrentBalance(), 0.005);
    }

    @Test
    @Order(31)
    void transactionHistorySupportsDecemberToJanuaryLastMonthBoundary() {
        int accountId = database.addAccount("Year Boundary Cash", "Cash", "MWK", "Cash", "", 500,
                LocalDate.of(2026, 12, 1).toString(), 0, "Year boundary regression", "", "ACTIVE", "test");
        Category category = database.findOrCreateCategory("Year Boundary Expenses", "EXPENSE");
        database.recordTransaction(accountId, category.getId(), null, null, "EXPENSE", "NORMAL", "COMPLETED",
                125, LocalDate.of(2026, 12, 31), "December year-boundary regression", "Cash", "DECEMBER-REGRESSION");
        database.recordTransaction(accountId, category.getId(), null, null, "EXPENSE", "NORMAL", "COMPLETED",
                40, LocalDate.of(2027, 1, 1), "January year-boundary regression", "Cash", "JANUARY-REGRESSION");

        LocalDate january2027 = LocalDate.of(2027, 1, 15);
        LocalDate lastMonthStart = january2027.minusMonths(1).withDayOfMonth(1);
        LocalDate lastMonthEnd = lastMonthStart.withDayOfMonth(lastMonthStart.lengthOfMonth());
        DatabaseHandler.TransactionHistoryPage page = database.listTransactionHistory(new DatabaseHandler.TransactionHistoryFilter(
                lastMonthStart,
                lastMonthEnd,
                accountId,
                "Expense",
                "",
                null,
                "REGRESSION",
                true,
                50,
                0
        ));

        assertEquals(LocalDate.of(2026, 12, 1), lastMonthStart);
        assertEquals(LocalDate.of(2026, 12, 31), lastMonthEnd);
        assertTrue(page.transactions().stream()
                .anyMatch(transaction -> "DECEMBER-REGRESSION".equals(transaction.getReferenceNumber())));
        assertFalse(page.transactions().stream()
                .anyMatch(transaction -> "JANUARY-REGRESSION".equals(transaction.getReferenceNumber())));
    }

    @Test
    @Order(32)
    void currencyRateEntryIsPreservedAsManualExchangeRateHistory() {
        database.saveCurrency("US Dollar", "USD", "USD", 1750.25, "ACTIVE");

        ExchangeRateQuote quote = database.findActiveManualExchangeRate("USD", "MWK", LocalDate.now())
                .orElseThrow();

        assertEquals("USD", quote.fromCurrency());
        assertEquals("MWK", quote.toCurrency());
        assertEquals(0, new BigDecimal("1750.25").compareTo(quote.rate()));
        assertEquals(ExchangeRateSource.MANUAL, quote.source());
        assertTrue(quote.manual());
        assertTrue(database.listExchangeRateHistory("USD", "MWK", 10).size() >= 1);
    }

    @Test
    @Order(33)
    void downloadedRatesRetainHistoryAndRejectMeaninglessRows() throws Exception {
        database.saveExchangeRate(new ExchangeRateQuote(
                "GBP",
                "MWK",
                new BigDecimal("2284.1000"),
                LocalDate.of(2026, 8, 10),
                Instant.parse("2026-08-10T18:20:00Z"),
                "Frankfurter",
                ExchangeRateSource.ONLINE,
                "ONLINE",
                false,
                false,
                ""
        ));
        database.saveExchangeRate(new ExchangeRateQuote(
                "GBP",
                "MWK",
                new BigDecimal("2290.2200"),
                LocalDate.of(2026, 8, 11),
                Instant.parse("2026-08-11T10:30:00Z"),
                "Frankfurter",
                ExchangeRateSource.ONLINE,
                "ONLINE",
                false,
                false,
                ""
        ));

        List<ExchangeRateQuote> history = database.listExchangeRateHistory("GBP", "MWK", 10);

        assertTrue(history.size() >= 2);
        assertEquals(0, new BigDecimal("2290.2200").compareTo(database.findLatestExchangeRate("GBP", "MWK").orElseThrow().rate()));
        assertThrows(IllegalArgumentException.class, () -> new ExchangeRateQuote(
                "USD",
                "USD",
                BigDecimal.ZERO,
                LocalDate.now(),
                Instant.now(),
                "Test",
                ExchangeRateSource.ONLINE,
                "ONLINE",
                false,
                false,
                ""
        ));
        assertEquals(0, sameCurrencyRateRows());
    }

    @Test
    @Order(34)
    void crossCurrencyTransferLocksSelectedExchangeRateIntoBothTransactionRows() throws Exception {
        database.saveCurrency("US Dollar", "USD", "USD", 1750, "ACTIVE");
        int sourceId = database.addAccount("FX Lock USD Source", "Cash", "USD", "Cash", "", 200,
                LocalDate.of(2026, 8, 11).toString(), 0, "FX source", "", "ACTIVE", "test");
        int destinationId = database.addAccount("FX Lock MWK Destination", "Cash", "MWK", "Cash", "", 0,
                LocalDate.of(2026, 8, 11).toString(), 0, "FX destination", "", "ACTIVE", "test");
        Instant rateTimestamp = Instant.parse("2026-08-11T10:30:00Z");
        TransferFxMetadata metadata = new TransferFxMetadata(
                new BigDecimal("100.00"),
                "USD",
                new BigDecimal("1750.0000"),
                new BigDecimal("175000.00"),
                "MWK",
                "ONLINE",
                rateTimestamp,
                "Frankfurter",
                "ONLINE",
                LocalDate.of(2026, 8, 11)
        );

        database.recordTransferWithFee(
                sourceId,
                destinationId,
                100,
                175000,
                0,
                null,
                LocalDate.of(2026, 8, 11),
                "locked FX transfer",
                "Cash",
                "FX-LOCK-REGRESSION",
                metadata
        );

        TransactionFxRow outgoing = transferFxRow("FX-LOCK-REGRESSION", "TRANSFER_OUT");
        TransactionFxRow incoming = transferFxRow("FX-LOCK-REGRESSION", "TRANSFER_IN");

        assertEquals(0, new BigDecimal("100.00").compareTo(outgoing.originalAmount()));
        assertEquals("USD", outgoing.originalCurrency());
        assertEquals(0, new BigDecimal("1750.0000").compareTo(outgoing.exchangeRate()));
        assertEquals(0, new BigDecimal("175000.00").compareTo(outgoing.convertedAmount()));
        assertEquals("MWK", outgoing.convertedCurrency());
        assertEquals("ONLINE", outgoing.exchangeRateSource());
        assertEquals(rateTimestamp.toString(), outgoing.exchangeRateTimestamp());
        assertEquals("Frankfurter", outgoing.exchangeRateProvider());
        assertEquals("ONLINE", outgoing.exchangeRateType());
        assertEquals("2026-08-11", outgoing.exchangeRateDate());
        assertEquals(outgoing, incoming);
        assertEquals(100, accountById(sourceId).getCurrentBalance(), 0.005);
        assertEquals(175000, accountById(destinationId).getCurrentBalance(), 0.005);
    }

    @Test
    @Order(35)
    void completedStandaloneExpenseCanBeSoftDeletedAndRestoredWithoutDuplicateBalanceEffects() throws Exception {
        int accountId = database.addAccount("Disposal Expense Cash", "Cash", "MWK", "Cash", "", 100000,
                LocalDate.of(2026, 8, 11).toString(), 0, "Deletion regression", "", "ACTIVE", "test");
        Category category = database.findOrCreateCategory("Public Transport Disposal", "EXPENSE");
        String description = "This is money i used for transport to and from work today";
        database.recordTransaction(
                accountId,
                category.getId(),
                null,
                null,
                null,
                "EXPENSE",
                "NORMAL",
                "COMPLETED",
                4000,
                LocalDate.of(2026, 8, 11),
                description,
                null,
                null
        );
        int transactionId = transactionIdByDescription(description);
        assertEquals(96000, accountById(accountId).getCurrentBalance(), 0.005);

        List<DatabaseHandler.RecordDisposalCandidateData> candidates = database.searchRecordDisposalCandidates(
                "Transaction",
                String.valueOf(transactionId),
                "All",
                null,
                null,
                "",
                false
        );
        assertEquals(1, candidates.size());
        DatabaseHandler.RecordDisposalCandidateData candidate = candidates.getFirst();
        assertEquals("ELIGIBLE", candidate.eligibility());
        assertEquals(0, candidate.dependencies());
        assertEquals("Soft delete available.", candidate.recommendation());

        DatabaseHandler.RecordDisposalImpact impact = database.previewRecordDisposalImpact(candidates);
        assertEquals(1, impact.eligibleRecords());
        assertEquals(0, impact.blockedRecords());
        assertEquals(4000, impact.balanceDifference(), 0.005);

        database.executeRecordDisposal(candidates, "Remove duplicate user-entered expense", "JUnit regression", "test-backup.db", "test-checksum");

        assertTrue(transactionDeletedFlag(transactionId));
        assertEquals(100000, accountById(accountId).getCurrentBalance(), 0.005);
        assertFalse(database.listTransactionsForAccount(accountId).stream().anyMatch(transaction -> transaction.getId() == transactionId));
        assertTrue(database.listDeletedRecords("Transaction", description, 10).stream()
                .anyMatch(record -> record.recordId() == transactionId));

        assertThrows(IllegalStateException.class, () -> database.executeRecordDisposal(
                candidates,
                "Duplicate delete attempt",
                "JUnit regression",
                "test-backup.db",
                "test-checksum"
        ));
        assertEquals(100000, accountById(accountId).getCurrentBalance(), 0.005);

        database.restoreDeletedRecord("Transaction", transactionId, "Restore expense deletion regression");

        assertFalse(transactionDeletedFlag(transactionId));
        assertEquals(96000, accountById(accountId).getCurrentBalance(), 0.005);
        assertTrue(database.listTransactionsForAccount(accountId).stream().anyMatch(transaction -> transaction.getId() == transactionId));
        assertEquals(1, transactionRowsByDescription(description));

        assertThrows(IllegalArgumentException.class, () -> database.restoreDeletedRecord("Transaction", transactionId, "Duplicate restore attempt"));
        assertEquals(96000, accountById(accountId).getCurrentBalance(), 0.005);
        assertEquals(1, transactionRowsByDescription(description));
    }

    @Test
    @Order(36)
    void openingBalanceTransactionRemainsProtectedFromStandaloneDeletion() throws Exception {
        int accountId = database.addAccount("Protected Opening Balance Cash", "Cash", "MWK", "Cash", "", 150000,
                LocalDate.of(2026, 8, 11).toString(), 0, "Opening balance deletion regression", "", "ACTIVE", "test");
        int transactionId = openingBalanceTransactionId(accountId);

        List<DatabaseHandler.RecordDisposalCandidateData> candidates = database.searchRecordDisposalCandidates(
                "Transaction",
                String.valueOf(transactionId),
                "All",
                null,
                null,
                "",
                false
        );

        assertEquals(1, candidates.size());
        DatabaseHandler.RecordDisposalCandidateData candidate = candidates.getFirst();
        assertEquals("NOT ELIGIBLE", candidate.eligibility());
        assertEquals("Use Edit Opening Balance from the account record.", candidate.recommendation());
        assertThrows(IllegalArgumentException.class, () -> database.softDeleteRecord("Transaction", transactionId, "Should stay protected"));
        assertFalse(transactionDeletedFlag(transactionId));
        assertEquals(150000, accountById(accountId).getCurrentBalance(), 0.005);
    }

    @Test
    @Order(37)
    void completedStandaloneIncomeCanBeSoftDeletedAndRestoredWithoutDuplicateBalanceEffects() throws Exception {
        int accountId = database.addAccount("Disposal Income Cash", "Cash", "MWK", "Cash", "", 100000,
                LocalDate.of(2026, 8, 11).toString(), 0, "Income deletion regression", "", "ACTIVE", "test");
        Category category = database.findOrCreateCategory("Consulting Disposal", "INCOME");
        String description = "Income disposal regression payment";
        int transactionId = database.recordIncomeTransaction(
                accountId,
                category.getId(),
                null,
                null,
                null,
                20000,
                "MWK",
                LocalDate.of(2026, 8, 11),
                description,
                "Cash",
                "INC-DISPOSAL-REGRESSION"
        );
        assertEquals(120000, accountById(accountId).getCurrentBalance(), 0.005);

        List<DatabaseHandler.RecordDisposalCandidateData> candidates = database.searchRecordDisposalCandidates(
                "Transaction",
                String.valueOf(transactionId),
                "All",
                null,
                null,
                "",
                false
        );

        assertEquals(1, candidates.size());
        DatabaseHandler.RecordDisposalCandidateData candidate = candidates.getFirst();
        assertEquals("ELIGIBLE", candidate.eligibility());
        assertEquals("Soft delete available.", candidate.recommendation());

        DatabaseHandler.RecordDisposalImpact impact = database.previewRecordDisposalImpact(candidates);
        assertEquals(1, impact.eligibleRecords());
        assertEquals(0, impact.blockedRecords());
        assertEquals(-20000, impact.balanceDifference(), 0.005);

        database.executeRecordDisposal(candidates, "Remove duplicate user-entered income", "JUnit regression", "test-backup.db", "test-checksum");

        assertTrue(transactionDeletedFlag(transactionId));
        assertEquals(100000, accountById(accountId).getCurrentBalance(), 0.005);
        assertFalse(database.listTransactionsForAccount(accountId).stream().anyMatch(transaction -> transaction.getId() == transactionId));

        database.restoreDeletedRecord("Transaction", transactionId, "Restore income deletion regression");

        assertFalse(transactionDeletedFlag(transactionId));
        assertEquals(120000, accountById(accountId).getCurrentBalance(), 0.005);
        assertTrue(database.listTransactionsForAccount(accountId).stream().anyMatch(transaction -> transaction.getId() == transactionId));

        assertThrows(IllegalArgumentException.class, () -> database.restoreDeletedRecord("Transaction", transactionId, "Duplicate restore attempt"));
        assertEquals(120000, accountById(accountId).getCurrentBalance(), 0.005);
    }

    @Test
    @Order(38)
    void linkedTransferTransactionsRemainProtectedFromStandaloneDeletion() throws Exception {
        int sourceId = database.addAccount("Protected Transfer Source", "Cash", "MWK", "Cash", "", 100000,
                LocalDate.of(2026, 8, 11).toString(), 0, "Transfer deletion regression", "", "ACTIVE", "test");
        int destinationId = database.addAccount("Protected Transfer Destination", "Cash", "MWK", "Cash", "", 20000,
                LocalDate.of(2026, 8, 11).toString(), 0, "Transfer deletion regression", "", "ACTIVE", "test");
        database.recordTransfer(sourceId, destinationId, 10000, 10000,
                LocalDate.of(2026, 8, 11), "protected transfer deletion regression", "Cash", "TRX-DELETE-PROTECTED");
        int outgoingId = transactionIdByReferencePurpose("TRX-DELETE-PROTECTED", "TRANSFER_OUT");

        assertEquals(90000, accountById(sourceId).getCurrentBalance(), 0.005);
        assertEquals(30000, accountById(destinationId).getCurrentBalance(), 0.005);

        List<DatabaseHandler.RecordDisposalCandidateData> candidates = database.searchRecordDisposalCandidates(
                "Transaction",
                String.valueOf(outgoingId),
                "All",
                null,
                null,
                "",
                false
        );

        assertEquals(1, candidates.size());
        DatabaseHandler.RecordDisposalCandidateData candidate = candidates.getFirst();
        assertEquals("NOT ELIGIBLE", candidate.eligibility());
        assertEquals("Use the linked transaction workflow.", candidate.recommendation());
        assertTrue(candidate.dependencies() > 0);

        assertThrows(IllegalArgumentException.class, () -> database.softDeleteRecord("Transaction", outgoingId, "Should require linked workflow"));
        assertFalse(transactionDeletedFlag(outgoingId));
        assertEquals(90000, accountById(sourceId).getCurrentBalance(), 0.005);
        assertEquals(30000, accountById(destinationId).getCurrentBalance(), 0.005);
    }

    private static Account accountById(int accountId) {
        return database.listAccounts().stream()
                .filter(account -> account.getId() == accountId)
                .findFirst()
                .orElseThrow();
    }

    private static int rowCount(String tableName) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static int sameCurrencyRateRows() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("""
                     SELECT COUNT(*)
                     FROM exchange_rates
                     WHERE upper(base_currency) = upper(quote_currency)
                        OR rate <= 0
                     """)) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private static TransactionFxRow transferFxRow(String referenceNumber, String purpose) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT original_amount, original_currency, exchange_rate, converted_amount, converted_currency,
                            exchange_rate_source, exchange_rate_timestamp, exchange_rate_provider,
                            exchange_rate_type, exchange_rate_date
                     FROM transactions
                     WHERE reference_number = ?
                       AND transaction_purpose = ?
                     LIMIT 1
                     """)) {
            statement.setString(1, referenceNumber);
            statement.setString(2, purpose);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return new TransactionFxRow(
                        decimalColumn(resultSet, "original_amount"),
                        resultSet.getString("original_currency"),
                        decimalColumn(resultSet, "exchange_rate"),
                        decimalColumn(resultSet, "converted_amount"),
                        resultSet.getString("converted_currency"),
                        resultSet.getString("exchange_rate_source"),
                        resultSet.getString("exchange_rate_timestamp"),
                        resultSet.getString("exchange_rate_provider"),
                        resultSet.getString("exchange_rate_type"),
                        resultSet.getString("exchange_rate_date")
                );
            }
        }
    }

    private static BigDecimal decimalColumn(ResultSet resultSet, String columnName) throws Exception {
        String value = resultSet.getString(columnName);
        return value == null ? null : new BigDecimal(value);
    }

    private static int accountRowsByName(String accountName) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM accounts WHERE account_name = ?"
             )) {
            statement.setString(1, accountName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int transactionRowsForAccount(int accountId) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM transactions WHERE account_id = ?"
             )) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int openingBalanceAuditCount(int accountId) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM transactions
                     WHERE account_id = ?
                       AND transaction_type = 'OPENING_BALANCE'
                       AND transaction_purpose = 'OPENING_BALANCE'
                       AND transaction_status = 'COMPLETED'
                     """)) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int openingBalanceTransactionId(int accountId) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id
                     FROM transactions
                     WHERE account_id = ?
                       AND transaction_type = 'OPENING_BALANCE'
                       AND transaction_purpose = 'OPENING_BALANCE'
                       AND transaction_status = 'COMPLETED'
                     ORDER BY id DESC
                     LIMIT 1
                     """)) {
            statement.setInt(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : -1;
            }
        }
    }

    private static int transactionIdByDescription(String description) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id
                     FROM transactions
                     WHERE description = ?
                     ORDER BY id DESC
                     LIMIT 1
                     """)) {
            statement.setString(1, description);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : -1;
            }
        }
    }

    private static int transactionRowsByDescription(String description) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*)
                     FROM transactions
                     WHERE description = ?
                     """)) {
            statement.setString(1, description);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : 0;
            }
        }
    }

    private static int transactionIdByReferencePurpose(String referenceNumber, String purpose) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT id
                     FROM transactions
                     WHERE reference_number = ?
                       AND transaction_purpose = ?
                     ORDER BY id DESC
                     LIMIT 1
                     """)) {
            statement.setString(1, referenceNumber);
            statement.setString(2, purpose);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getInt(1) : -1;
            }
        }
    }

    private static boolean transactionDeletedFlag(int transactionId) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COALESCE(is_deleted, 0)
                     FROM transactions
                     WHERE id = ?
                     """)) {
            statement.setInt(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1;
            }
        }
    }

    private static void insertLedgerTransaction(
            int accountId,
            String transactionType,
            String transactionPurpose,
            String transactionStatus,
            double amount
    ) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO transactions (
                         account_id, transaction_type, transaction_purpose, transaction_status,
                         amount, transaction_date, description, source
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, 'TEST')
                     """)) {
            statement.setInt(1, accountId);
            statement.setString(2, transactionType);
            statement.setString(3, transactionPurpose);
            statement.setString(4, transactionStatus);
            statement.setDouble(5, amount);
            statement.setString(6, LocalDate.now().toString());
            statement.setString(7, "Trigger acceptance regression for " + transactionType + "/" + transactionPurpose);
            statement.executeUpdate();
        }
    }

    private static boolean triggerExists(String triggerName) throws Exception {
        return triggerSql(triggerName) != null;
    }

    private static String triggerSql(String triggerName) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT sql
                     FROM sqlite_master
                     WHERE type = 'trigger'
                       AND name = ?
                     """)) {
            statement.setString(1, triggerName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getString("sql") : null;
            }
        }
    }

    private static void assertCanonicalTransactionTrigger(String triggerName) throws Exception {
        String sql = triggerSql(triggerName);
        assertNotNull(sql);
        String upperSql = sql.toUpperCase(Locale.ENGLISH);
        assertTrue(upperSql.contains("OPENING_BALANCE"));
        assertTrue(upperSql.contains("ASSET_SALE"));
        assertTrue(upperSql.contains("ADJUSTMENT"));
        assertTrue(upperSql.contains("LOAN"));
        assertTrue(upperSql.contains("FROZEN"));
        assertTrue(upperSql.contains("LOAN_SETTLEMENT"));
        assertTrue(upperSql.contains("COMMUNITY_LOAN_RECEIVABLE_INCREASE"));
        assertFalse(upperSql.contains("POSTING_STATUS"));
        assertFalse(upperSql.contains("SETTLEMENT_STATUS"));
    }

    private static boolean columnExists(String tableName, String columnName) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                if (columnName.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean migrationHistoryExists(String migrationKey) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT migration_key FROM schema_migration_history WHERE migration_key = '" + migrationKey + "'"
             )) {
            return resultSet.next();
        }
    }

    private static int accountSystemFlag(String accountName) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DatabaseHandler.databasePath());
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COALESCE(is_system_account, 0) FROM accounts WHERE account_name = '" + accountName + "'"
             )) {
            return resultSet.next() ? resultSet.getInt(1) : -1;
        }
    }

    private record TransactionFxRow(
            BigDecimal originalAmount,
            String originalCurrency,
            BigDecimal exchangeRate,
            BigDecimal convertedAmount,
            String convertedCurrency,
            String exchangeRateSource,
            String exchangeRateTimestamp,
            String exchangeRateProvider,
            String exchangeRateType,
            String exchangeRateDate
    ) {
    }
}
