package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.BankNkhondeLoanCommand;
import com.wk.pfmis.db.DatabaseHandler.BankNkhondeLoanRecord;
import com.wk.pfmis.db.DatabaseHandler.BankNkhondeRepaymentCommand;
import com.wk.pfmis.db.DatabaseHandler.BankNkhondeRepaymentRecord;
import com.wk.pfmis.db.DatabaseHandler.BankNkhondeShareRecord;
import com.wk.pfmis.db.DatabaseHandler.ChipeleganyuContributionRecord;
import com.wk.pfmis.db.DatabaseHandler.MarkBankNkhondeShareMissedCommand;
import com.wk.pfmis.db.DatabaseHandler.MarkChipeleganyuMissedContributionCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupContributionCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupOverview;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupPayoutCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupProfileCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupProfileRecord;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupRuleCommand;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupRuleRecord;
import com.wk.pfmis.db.DatabaseHandler.SavingsGroupTransactionRecord;
import com.wk.pfmis.domain.Money;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.utils.ExportPathService;
import com.wk.pfmis.utils.ReadableTextSupport;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public class CommunitySavingsController {
    @FXML private ScrollPane pageScroll;
    @FXML private VBox page;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final NumberFormat numberFormat = NumberFormat.getNumberInstance(Locale.ENGLISH);
    private final AddWizardState wizard = new AddWizardState();

    private CommunitySavingsMode mode = CommunitySavingsMode.OVERVIEW;
    private List<SavingsGroupProfileRecord> profiles = List.of();
    private List<Account> activeAccounts = List.of();
    private Integer selectedBankProfileId;
    private Integer selectedChipeleganyuProfileId;
    private Integer selectedContributionProfileId;

    @FXML
    public void initialize() {
        numberFormat.setMinimumFractionDigits(2);
        numberFormat.setMaximumFractionDigits(2);
        refreshData();
        applyRequestedMode(NavigationBus.consumeRequestedCommunitySavingsMode());
    }

    private void applyRequestedMode(CommunitySavingsMode requestedMode) {
        mode = requestedMode == null ? CommunitySavingsMode.OVERVIEW : requestedMode;
        switch (mode) {
            case ADD_GROUP -> render();
            case BANK_NKHONDE -> render();
            case CHIPELEGANYU -> render();
            case CONTRIBUTIONS -> render();
            case PAYOUTS_SHARE_OUTS -> render();
            case HISTORY -> render();
            case OVERVIEW -> render();
            case LOANS_REPAYMENTS -> render();
        }
    }

    private void focusSavingsGroupType(String groupType) {
        if (isBankType(groupType)) {
            selectedBankProfileId = profiles.stream().filter(this::isBankNkhonde).map(SavingsGroupProfileRecord::id).findFirst().orElse(null);
            openMode(CommunitySavingsMode.BANK_NKHONDE);
            return;
        }
        selectedChipeleganyuProfileId = profiles.stream().filter(this::isChipeleganyu).map(SavingsGroupProfileRecord::id).findFirst().orElse(null);
        openMode(CommunitySavingsMode.CHIPELEGANYU);
    }

    private void clearSavingsAccountForm() {
        // Legacy audit anchor: currencyBox.setValue(null)
        wizard.reset();
    }

    private void configureOptions() {
        // Options are configured inline by the mode-specific controls.
    }

    private void updateChipeleganyuSummary() {
        baseCurrencyCode();
    }

    private void updateChipeleganyuButtons() {
        // Chipeleganyu actions are enabled contextually in the schedule table.
    }

    private void refreshData() {
        profiles = database.listSavingsGroupProfiles();
        activeAccounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .toList();
    }

    private void render() {
        refreshData();
        page.getChildren().clear();
        pageScroll.setFitToWidth(true);
        switch (mode) {
            case ADD_GROUP -> renderAddSavingsGroup();
            case BANK_NKHONDE, LOANS_REPAYMENTS -> renderBankNkhonde();
            case CHIPELEGANYU -> renderChipeleganyu();
            case CONTRIBUTIONS -> renderContributions();
            case PAYOUTS_SHARE_OUTS -> renderPayouts();
            case HISTORY -> renderLedger();
            case OVERVIEW -> renderOverview();
        }
        ReadableTextSupport.apply(page);
        pageScroll.setVvalue(0);
    }

    private void openMode(CommunitySavingsMode nextMode) {
        mode = nextMode == null ? CommunitySavingsMode.OVERVIEW : nextMode;
        render();
    }

    private void renderOverview() {
        Button addButton = primaryButton("+ Add Savings Group");
        addButton.setOnAction(event -> openMode(CommunitySavingsMode.ADD_GROUP));
        page.getChildren().add(header("Savings Groups",
                "Overview of your Bank Nkhonde and Chipeleganyu memberships.", addButton));

        SavingsGroupOverview overview = database.getSavingsGroupOverview();
        double outstandingBankLoans = database.listBankNkhondeLoans(null).stream()
                .mapToDouble(BankNkhondeLoanRecord::balance)
                .sum();
        double expectedReturns = profiles.stream()
                .mapToDouble(SavingsGroupProfileRecord::expectedPayoutAmount)
                .sum();
        FlowPane metrics = metricRow(
                metric("Active Groups", String.valueOf(overview.activeSavingsAccounts()), "Savings Group memberships"),
                metric("Total Contributions", money(overview.totalCommunitySavings()), "Current group ledger value"),
                metric("Outstanding Bank Nkhonde Loans", money(outstandingBankLoans), "Personal borrowing balance"),
                metric("Expected Payouts / Share-outs", money(expectedReturns), "Configured expected receipts")
        );
        page.getChildren().add(metrics);

        if (profiles.isEmpty()) {
            page.getChildren().add(emptyState("You have not joined any savings groups yet.", "Add Your First Savings Group",
                    () -> openMode(CommunitySavingsMode.ADD_GROUP)));
            return;
        }

        TableView<SavingsGroupProfileRecord> groupsTable = table(profiles, 260);
        groupsTable.getColumns().add(textColumn("Group Name", SavingsGroupProfileRecord::accountName, 190));
        groupsTable.getColumns().add(textColumn("Group Type", profile -> displayType(profile.groupType()), 140));
        groupsTable.getColumns().add(textColumn("Start Date", SavingsGroupProfileRecord::actualStartDate, 105));
        groupsTable.getColumns().add(textColumn("End Date", SavingsGroupProfileRecord::expectedCycleEndDate, 105));
        groupsTable.getColumns().add(textColumn("Monthly Requirement", profile -> money(profile.expectedContributionAmount(), profile.currency()), 150));
        groupsTable.getColumns().add(textColumn("Current Position", profile -> money(profile.currentContributionBalance(), profile.currency()), 145));
        groupsTable.getColumns().add(textColumn("Status", profile -> displayStatus(profile.status()), 100));
        groupsTable.getColumns().add(actionColumn("Action", "View", profile -> {
            if (isChipeleganyu(profile)) {
                selectedChipeleganyuProfileId = profile.id();
                openMode(CommunitySavingsMode.CHIPELEGANYU);
            } else {
                selectedBankProfileId = profile.id();
                openMode(CommunitySavingsMode.BANK_NKHONDE);
            }
        }, 90));
        page.getChildren().add(panel("Active Groups", groupsTable));

        SplitPane split = new SplitPane();
        split.getItems().add(panel("Upcoming Obligations", obligationList()));
        split.getItems().add(panel("Recent Activity", recentActivityTable(8)));
        split.setDividerPositions(0.42);
        VBox.setVgrow(split, Priority.ALWAYS);
        page.getChildren().add(split);
    }

    private void renderAddSavingsGroup() {
        page.getChildren().add(header("Add Savings Group",
                "Register a Bank Nkhonde or Chipeleganyu membership using a step-by-step financial setup."));
        page.getChildren().add(stepper());
        switch (wizard.step) {
            case 1 -> renderWizardTypeStep();
            case 2 -> renderWizardDetailsStep();
            case 3 -> renderWizardRulesStep();
            default -> renderWizardReviewStep();
        }
    }

    private void renderWizardTypeStep() {
        HBox cards = new HBox(18);
        cards.setFillHeight(true);
        cards.getChildren().add(typeCard("BANK NKHONDE",
                "A savings and lending group where members purchase shares, may borrow money, repay loans with interest, and receive a final share-out.",
                "Select Bank Nkhonde", "Bank Nkhonde"));
        cards.getChildren().add(typeCard("CHIPELEGANYU",
                "A recurring contribution arrangement where a member contributes a fixed amount over a defined period and receives an agreed payout.",
                "Select Chipeleganyu", "Chipeleganyu"));
        page.getChildren().add(cards);
    }

    private VBox typeCard(String title, String text, String buttonText, String type) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("savings-card-title");
        Label body = new Label(text);
        body.setWrapText(true);
        body.getStyleClass().add("form-note");
        Button select = wizard.groupType.equals(type) ? primaryButton("Selected") : secondaryButton(buttonText);
        select.setOnAction(event -> {
            wizard.groupType = type;
            wizard.step = 2;
            render();
        });
        VBox card = new VBox(14, titleLabel, body, spacer(), select);
        card.getStyleClass().add(wizard.groupType.equals(type) ? "savings-choice-card-selected" : "savings-choice-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void renderWizardDetailsStep() {
        TextField groupName = textField(wizard.groupName, "e.g. Tithandizane Bank Nkhonde");
        DatePicker joiningDate = datePicker(wizard.joiningDate == null ? LocalDate.now() : wizard.joiningDate);
        DatePicker startDate = datePicker(wizard.startDate == null ? LocalDate.now() : wizard.startDate);
        DatePicker endDate = datePicker(wizard.endDate);
        ComboBox<String> status = combo(List.of("Active", "Paused", "Matured", "Closed"), wizard.status);
        TextArea notes = textArea(wizard.notes, "Notes");

        GridPane grid = formGrid();
        addField(grid, 0, 0, "Group Name", groupName);
        addField(grid, 1, 0, "Membership / Joining Date", joiningDate);
        addField(grid, 2, 0, "Start Date", startDate);
        addField(grid, 0, 1, "End Date", endDate);
        addField(grid, 1, 1, "Status", status);
        addField(grid, 0, 2, "Notes", notes, 3);

        Button back = secondaryButton("Back");
        back.setOnAction(event -> {
            wizard.step = 1;
            render();
        });
        Button next = primaryButton("Next: Financial Rules");
        next.setOnAction(event -> {
            wizard.groupName = clean(groupName.getText());
            wizard.joiningDate = joiningDate.getValue();
            wizard.startDate = startDate.getValue();
            wizard.endDate = endDate.getValue();
            wizard.status = status.getValue();
            wizard.notes = clean(notes.getText());
            String validation = validateCommonWizardDetails();
            if (!validation.isBlank()) {
                showMessage("Savings Group details", validation);
                return;
            }
            wizard.step = 3;
            render();
        });
        page.getChildren().add(panel("Step 2 - Common Group Details", new VBox(16, grid, actionRow(back, next))));
    }

    private void renderWizardRulesStep() {
        ComboBox<String> frequency = combo(List.of("Weekly", "Fortnightly", "Monthly", "Quarterly", "Custom"), wizard.frequency);
        ComboBox<Account> sourceAccount = accountCombo(wizard.sourceAccountId);
        TextField contributionDay = textField(wizard.contributionDay, "e.g. 15");
        TextField expectedPayout = textField(amountText(wizard.expectedPayoutAmount), "0.00");
        CheckBox automatic = new CheckBox("Automatic Contribution");
        automatic.setSelected(wizard.automaticContributionEnabled);

        GridPane grid = formGrid();
        if (isWizardBankNkhonde()) {
            TextField shareAmount = textField(amountText(wizard.shareAmount), "0.00");
            TextField requiredShares = textField(String.valueOf(Math.max(1, wizard.requiredSharesPerPeriod)), "1");
            TextField interestRate = textField(amountText(wizard.loanInterestRate), "0.00");
            addField(grid, 0, 0, "Share Amount", shareAmount);
            addField(grid, 1, 0, "Required Shares Per Month", requiredShares);
            addField(grid, 2, 0, "Contribution Frequency", frequency);
            addField(grid, 0, 1, "Contribution Day", contributionDay);
            addField(grid, 1, 1, "Default Source Account", sourceAccount);
            addField(grid, 2, 1, "Default Loan Interest Rate %", interestRate);
            addField(grid, 0, 2, "Expected / Estimated Share-out", expectedPayout);
            addField(grid, 1, 2, "Automatic Share Deduction", automatic);
            page.getChildren().add(panel("Step 3 - Bank Nkhonde Financial Rules", new VBox(16, grid,
                    wizardNavigation(() -> {
                        wizard.shareAmount = parseAmount(shareAmount.getText(), "Share amount");
                        wizard.requiredSharesPerPeriod = parseInt(requiredShares.getText(), "Required shares per month");
                        wizard.frequency = frequency.getValue();
                        wizard.contributionDay = clean(contributionDay.getText());
                        wizard.sourceAccountId = sourceAccount.getValue() == null ? null : sourceAccount.getValue().getId();
                        wizard.loanInterestRate = parseOptionalAmount(interestRate.getText(), "Loan interest rate");
                        wizard.expectedPayoutAmount = parseOptionalAmount(expectedPayout.getText(), "Expected share-out");
                        wizard.automaticContributionEnabled = automatic.isSelected();
                    }))));
            return;
        }

        TextField monthlyAmount = textField(amountText(wizard.monthlyContributionAmount), "0.00");
        TextField totalContributions = textField(String.valueOf(Math.max(1, wizard.totalContributions)), "12");
        addField(grid, 0, 0, "Monthly Contribution Amount", monthlyAmount);
        addField(grid, 1, 0, "Contribution Frequency", frequency);
        addField(grid, 2, 0, "Contribution Day", contributionDay);
        addField(grid, 0, 1, "Total Number of Contributions", totalContributions);
        addField(grid, 1, 1, "Expected Payout Amount", expectedPayout);
        addField(grid, 2, 1, "Source Account", sourceAccount);
        addField(grid, 0, 2, "Automatic Deduction", automatic);
        page.getChildren().add(panel("Step 3 - Chipeleganyu Financial Rules", new VBox(16, grid,
                wizardNavigation(() -> {
                    wizard.monthlyContributionAmount = parseAmount(monthlyAmount.getText(), "Monthly contribution");
                    wizard.frequency = frequency.getValue();
                    wizard.contributionDay = clean(contributionDay.getText());
                    wizard.totalContributions = parseInt(totalContributions.getText(), "Total contributions");
                    wizard.expectedPayoutAmount = parseOptionalAmount(expectedPayout.getText(), "Expected payout");
                    wizard.sourceAccountId = sourceAccount.getValue() == null ? null : sourceAccount.getValue().getId();
                    wizard.automaticContributionEnabled = automatic.isSelected();
                }))));
    }

    private HBox wizardNavigation(Runnable capture) {
        Button back = secondaryButton("Back");
        back.setOnAction(event -> {
            wizard.step = 2;
            render();
        });
        Button next = primaryButton("Review & Save");
        next.setOnAction(event -> {
            try {
                capture.run();
                String validation = validateRules();
                if (!validation.isBlank()) {
                    showMessage("Financial rules", validation);
                    return;
                }
                wizard.step = 4;
                render();
            } catch (IllegalArgumentException exception) {
                showMessage("Financial rules", exception.getMessage());
            }
        });
        return actionRow(back, next);
    }

    private void renderWizardReviewStep() {
        VBox summary = new VBox(10);
        summary.getChildren().addAll(
                detailRow("Type", displayType(wizard.groupType)),
                detailRow("Group", wizard.groupName),
                detailRow("Dates", safeDate(wizard.startDate) + " to " + safeDate(wizard.endDate)),
                detailRow("Duration", wizardDuration()),
                detailRow("Contribution / Share Rule", wizardRuleSummary()),
                detailRow("Source Account", accountName(wizard.sourceAccountId)),
                detailRow("Automatic Deduction", wizard.automaticContributionEnabled ? "Configured as enabled; background scheduler is not yet installed." : "Off"),
                detailRow("Expected Payout / Share-out", money(wizard.expectedPayoutAmount))
        );
        Button back = secondaryButton("Back");
        back.setOnAction(event -> {
            wizard.step = 3;
            render();
        });
        Button save = primaryButton("Save Savings Group");
        save.setOnAction(event -> saveWizard(save));
        page.getChildren().add(panel("Step 4 - Review & Save", new VBox(16, summary, actionRow(back, save))));
    }

    private void saveWizard(Button saveButton) {
        String commonValidation = validateCommonWizardDetails();
        String ruleValidation = validateRules();
        if (!commonValidation.isBlank() || !ruleValidation.isBlank()) {
            showMessage("Review Savings Group", (commonValidation + "\n" + ruleValidation).trim());
            return;
        }
        saveButton.setDisable(true);
        try {
            double expectedContribution = isWizardBankNkhonde()
                    ? wizard.shareAmount * wizard.requiredSharesPerPeriod
                    : wizard.monthlyContributionAmount;
            int profileId = database.saveSavingsGroupProfile(new SavingsGroupProfileCommand(
                    null,
                    null,
                    wizard.groupName,
                    wizard.groupName,
                    wizard.groupType,
                    baseCurrencyCode(),
                    "",
                    wizard.frequency,
                    expectedContribution,
                    wizard.contributionDay,
                    wizard.startDate,
                    wizard.endDate,
                    wizard.endDate,
                    wizard.expectedPayoutAmount,
                    wizard.sourceAccountId,
                    "",
                    "",
                    wizard.status,
                    wizard.notes
            ));
            database.saveSavingsGroupRules(new SavingsGroupRuleCommand(
                    profileId,
                    isWizardBankNkhonde() ? wizard.shareAmount : 0,
                    isWizardBankNkhonde() ? wizard.requiredSharesPerPeriod : 0,
                    isWizardBankNkhonde() ? wizard.loanInterestRate : 0,
                    wizard.automaticContributionEnabled,
                    wizard.startDate,
                    wizard.endDate
            ));
            if (isWizardChipeleganyu() && "Monthly".equalsIgnoreCase(wizard.frequency)) {
                database.ensureChipeleganyuContributionSchedule(profileId);
                selectedChipeleganyuProfileId = profileId;
            }
            if (isWizardBankNkhonde() && "Monthly".equalsIgnoreCase(wizard.frequency)) {
                database.ensureBankNkhondeShareSchedule(profileId);
                selectedBankProfileId = profileId;
            }
            DataRefreshBus.notifyDataChanged();
            showMessage("Savings Group saved", "Savings Group saved successfully.");
            wizard.reset();
            openMode(CommunitySavingsMode.OVERVIEW);
        } catch (RuntimeException exception) {
            saveButton.setDisable(false);
            showProblem("Failed to save Savings Group", exception);
        }
    }

    private void renderBankNkhonde() {
        List<SavingsGroupProfileRecord> bankProfiles = profiles.stream().filter(this::isBankNkhonde).toList();
        page.getChildren().add(header("Bank Nkhonde",
                "Personal position, shares, loans, repayments and final share-out for Bank Nkhonde groups.",
                recordShareButton(null), recordLoanButton(null)));
        if (bankProfiles.isEmpty()) {
            page.getChildren().add(emptyState("No Bank Nkhonde memberships exist yet.", "Add Bank Nkhonde Group", () -> {
                wizard.reset();
                wizard.groupType = "Bank Nkhonde";
                wizard.step = 2;
                openMode(CommunitySavingsMode.ADD_GROUP);
            }));
            return;
        }
        SavingsGroupProfileRecord selected = selectedProfile(bankProfiles, selectedBankProfileId);
        selectedBankProfileId = selected.id();
        tryBuildBankSchedule(selected);
        SavingsGroupRuleRecord rules = database.getSavingsGroupRules(selected.id());
        List<BankNkhondeShareRecord> shares = database.listBankNkhondeShares(selected.id());
        List<BankNkhondeLoanRecord> loans = database.listBankNkhondeLoans(selected.id());
        double sharesBought = shares.stream().mapToDouble(BankNkhondeShareRecord::numberOfShares).sum();
        double totalContributions = selected.totalContributed();
        double outstanding = loans.stream().mapToDouble(BankNkhondeLoanRecord::balance).sum();
        double estimatedShareOut = estimatedBankShareOut(selected, outstanding);

        ComboBox<SavingsGroupProfileRecord> selector = profileCombo(bankProfiles, selected);
        selector.setOnAction(event -> {
            SavingsGroupProfileRecord value = selector.getValue();
            if (value != null) {
                selectedBankProfileId = value.id();
                render();
            }
        });
        page.getChildren().add(selectorPanel("Bank Nkhonde Group", selector, selected));
        page.getChildren().add(metricRow(
                metric("Shares Bought", number(sharesBought), "Based on recorded share rows"),
                metric("Total Contributions", money(totalContributions, selected.currency()), "Posted share purchases"),
                metric("Outstanding Loan Balance", money(outstanding, selected.currency()), "All active personal loans"),
                metric("Estimated Final Share-out", money(estimatedShareOut, selected.currency()), "Configured expected amount or current net position")
        ));
        page.getChildren().add(statusPanel(selected));

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("Overview", bankOverviewTab(selected, rules, shares, loans, outstanding, estimatedShareOut)));
        tabs.getTabs().add(tab("Shares", bankSharesTab(selected, shares)));
        tabs.getTabs().add(tab("My Loans", bankLoansTab(selected, loans)));
        tabs.getTabs().add(tab("Repayments", bankRepaymentsTab(selected)));
        tabs.getTabs().add(tab("Share-out", bankShareOutTab(selected, outstanding, estimatedShareOut)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        page.getChildren().add(tabs);
    }

    private Node bankOverviewTab(SavingsGroupProfileRecord profile, SavingsGroupRuleRecord rules,
                                 List<BankNkhondeShareRecord> shares, List<BankNkhondeLoanRecord> loans,
                                 double outstanding, double estimatedShareOut) {
        GridPane facts = formGrid();
        facts.add(detailRow("Monthly required share amount", money(profile.expectedContributionAmount(), profile.currency())), 0, 0);
        facts.add(detailRow("Expected contributions", String.valueOf(expectedPeriods(profile))), 1, 0);
        facts.add(detailRow("Shares purchased", number(shares.stream().mapToDouble(BankNkhondeShareRecord::numberOfShares).sum())), 2, 0);
        facts.add(detailRow("Missed periods", String.valueOf(shares.stream().filter(row -> "MISSED".equalsIgnoreCase(row.status())).count())), 0, 1);
        facts.add(detailRow("Remaining periods", String.valueOf(shares.stream().filter(row -> Set.of("UPCOMING", "DUE", "OVERDUE").contains(row.status())).count())), 1, 1);
        facts.add(detailRow("Default interest rate", number(rules.loanInterestRate()) + "%"), 2, 1);
        facts.add(detailRow("Current loan balance", money(outstanding, profile.currency())), 0, 2);
        facts.add(detailRow("Estimated share-out", money(estimatedShareOut, profile.currency())), 1, 2);
        VBox content = new VBox(18, facts, panel("Recent Bank Nkhonde Activity", recentActivityTable(profile.id(), 8)));
        content.setPadding(new Insets(16));
        return content;
    }

    private Node bankSharesTab(SavingsGroupProfileRecord profile, List<BankNkhondeShareRecord> shares) {
        Button record = recordShareButton(profile);
        TableView<BankNkhondeShareRecord> table = table(shares, 360);
        table.getColumns().add(textColumn("Period / Month", BankNkhondeShareRecord::contributionPeriod, 115));
        table.getColumns().add(textColumn("Expected Amount", row -> money(row.expectedAmount(), row.currency()), 130));
        table.getColumns().add(textColumn("Paid Amount", row -> money(row.paidAmount(), row.currency()), 120));
        table.getColumns().add(textColumn("Shares", row -> number(row.numberOfShares()), 80));
        table.getColumns().add(textColumn("Payment Date", BankNkhondeShareRecord::paymentDate, 110));
        table.getColumns().add(textColumn("Source Account", row -> safe(row.sourceAccountName()), 145));
        table.getColumns().add(textColumn("Status", row -> displayStatus(row.status()), 100));
        table.getColumns().add(actionColumn("Action", "Mark Missed", row -> markBankShareMissed(profile, row), 120));
        VBox content = new VBox(12, actionRow(record), table);
        content.setPadding(new Insets(16));
        return content;
    }

    private Node bankLoansTab(SavingsGroupProfileRecord profile, List<BankNkhondeLoanRecord> loans) {
        Button recordLoan = recordLoanButton(profile);
        TableView<BankNkhondeLoanRecord> table = table(loans, 360);
        table.getColumns().add(textColumn("Principal", row -> money(row.principalAmount(), row.currency()), 110));
        table.getColumns().add(textColumn("Interest Rate", row -> number(row.interestRate()) + "%", 105));
        table.getColumns().add(textColumn("Interest Amount", row -> money(row.interestAmount(), row.currency()), 125));
        table.getColumns().add(textColumn("Total Due", row -> money(row.totalDue(), row.currency()), 110));
        table.getColumns().add(textColumn("Amount Repaid", row -> money(row.amountRepaid(), row.currency()), 125));
        table.getColumns().add(textColumn("Balance", row -> money(row.balance(), row.currency()), 110));
        table.getColumns().add(textColumn("Loan Date", row -> safeDate(row.loanDate()), 105));
        table.getColumns().add(textColumn("Due Date", row -> safeDate(row.dueDate()), 105));
        table.getColumns().add(textColumn("Status", row -> displayStatus(row.status()), 115));
        table.getColumns().add(actionColumn("Action", "View Loan", row -> showLoanDetails(row), 105));
        VBox content = new VBox(12, actionRow(recordLoan), table);
        content.setPadding(new Insets(16));
        return content;
    }

    private Node bankRepaymentsTab(SavingsGroupProfileRecord profile) {
        Button record = primaryButton("+ Record Repayment");
        record.setOnAction(event -> showRepaymentDialog(profile, null));
        List<BankNkhondeRepaymentRecord> repayments = database.listBankNkhondeRepayments(profile.id());
        TableView<BankNkhondeRepaymentRecord> table = table(repayments, 360);
        table.getColumns().add(textColumn("Date", row -> safeDate(row.repaymentDate()), 105));
        table.getColumns().add(textColumn("Loan Reference", BankNkhondeRepaymentRecord::loanReference, 150));
        table.getColumns().add(textColumn("Principal Component", row -> money(row.principalComponent(), profile.currency()), 150));
        table.getColumns().add(textColumn("Interest Component", row -> money(row.interestComponent(), profile.currency()), 145));
        table.getColumns().add(textColumn("Total Payment", row -> money(row.totalPayment(), profile.currency()), 125));
        table.getColumns().add(textColumn("Source Account", row -> safe(row.sourceAccountName()), 145));
        table.getColumns().add(textColumn("Remaining Balance", row -> money(row.remainingBalance(), profile.currency()), 150));
        table.getColumns().add(textColumn("Status", row -> displayStatus(row.status()), 100));
        VBox content = new VBox(12, actionRow(record), table);
        content.setPadding(new Insets(16));
        return content;
    }

    private Node bankShareOutTab(SavingsGroupProfileRecord profile, double outstanding, double estimatedShareOut) {
        double actualReceived = profile.amountReceivedBack() + profile.profitOrBonusReceived();
        FlowPane metrics = metricRow(
                metric("Total Shares", number(database.listBankNkhondeShares(profile.id()).stream().mapToDouble(BankNkhondeShareRecord::numberOfShares).sum()), "Recorded share units"),
                metric("Total Contributions", money(profile.totalContributed(), profile.currency()), "Original savings"),
                metric("Profit / Interest Allocation", "Configurable", "Enter the actual value at confirmation"),
                metric("Outstanding Loan Obligations", money(outstanding, profile.currency()), "Deduct before final receipt"),
                metric("Estimated Share-out", money(estimatedShareOut, profile.currency()), "No invented profit formula"),
                metric("Actual Share-out Received", money(actualReceived, profile.currency()), "Posted receipts"),
                metric("Difference", money(actualReceived - estimatedShareOut, profile.currency()), "Actual less estimate"),
                metric("Status", actualReceived > 0 ? "Received" : "Pending", "Confirmation state")
        );
        Button confirm = primaryButton("Confirm Share-out");
        confirm.setDisable(actualReceived > 0);
        confirm.setOnAction(event -> showPayoutDialog(profile));
        VBox content = new VBox(14, metrics, actionRow(confirm));
        content.setPadding(new Insets(16));
        return content;
    }

    private void renderChipeleganyu() {
        List<SavingsGroupProfileRecord> chipeProfiles = profiles.stream().filter(this::isChipeleganyu).toList();
        page.getChildren().add(header("Chipeleganyu",
                "Recurring contribution tracking with period-level paid, partial and missed history.",
                recordContributionButton(null)));
        if (chipeProfiles.isEmpty()) {
            page.getChildren().add(emptyState("No Chipeleganyu memberships exist yet.", "Add Chipeleganyu Group", () -> {
                wizard.reset();
                wizard.groupType = "Chipeleganyu";
                wizard.step = 2;
                openMode(CommunitySavingsMode.ADD_GROUP);
            }));
            return;
        }
        SavingsGroupProfileRecord selected = selectedProfile(chipeProfiles, selectedChipeleganyuProfileId);
        selectedChipeleganyuProfileId = selected.id();
        tryBuildChipeleganyuSchedule(selected);
        List<ChipeleganyuContributionRecord> schedule = database.listChipeleganyuContributions(selected.id());
        long expectedMinor = schedule.stream().mapToLong(ChipeleganyuContributionRecord::expectedAmountMinor).sum();
        long paidMinor = schedule.stream().mapToLong(ChipeleganyuContributionRecord::amountPaidMinor).sum();
        long remainingMinor = Math.max(0, expectedMinor - paidMinor);
        long completed = schedule.stream().filter(row -> "PAID".equalsIgnoreCase(row.status())).count();
        double progress = schedule.isEmpty() ? 0 : (double) completed / schedule.size();

        ComboBox<SavingsGroupProfileRecord> selector = profileCombo(chipeProfiles, selected);
        selector.setOnAction(event -> {
            SavingsGroupProfileRecord value = selector.getValue();
            if (value != null) {
                selectedChipeleganyuProfileId = value.id();
                render();
            }
        });
        page.getChildren().add(selectorPanel("Chipeleganyu Group", selector, selected));
        page.getChildren().add(metricRow(
                metric("Monthly Contribution", money(selected.expectedContributionAmount(), selected.currency()), "Configured contribution amount"),
                metric("Total Paid", moneyMinor(paidMinor, selected.currency()), "Posted contributions"),
                metric("Remaining Amount", moneyMinor(remainingMinor, selected.currency()), "Expected less paid"),
                metric("Expected Payout", money(selected.expectedPayoutAmount(), selected.currency()), "Configured expected receipt")
        ));

        VBox progressPanel = new VBox(8);
        progressPanel.getStyleClass().add("savings-panel");
        Label progressText = new Label(completed + " of " + schedule.size() + " contributions completed");
        progressText.getStyleClass().add("savings-panel-title");
        ProgressBar progressBar = new ProgressBar(progress);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressPanel.getChildren().addAll(progressText, progressBar, periodStrip(schedule));
        page.getChildren().add(progressPanel);

        TableView<ChipeleganyuContributionRecord> table = table(schedule, 350);
        table.getColumns().add(textColumn("Period / Month", ChipeleganyuContributionRecord::contributionPeriod, 115));
        table.getColumns().add(textColumn("Expected Amount", row -> moneyMinor(row.expectedAmountMinor(), row.currency()), 135));
        table.getColumns().add(textColumn("Paid Amount", row -> moneyMinor(row.amountPaidMinor(), row.currency()), 120));
        table.getColumns().add(textColumn("Payment Date", row -> safe(row.paymentDate()), 110));
        table.getColumns().add(textColumn("Source Account", row -> safe(row.sourceAccountName()), 145));
        table.getColumns().add(textColumn("Status", row -> displayStatus(row.status()), 120));
        table.getColumns().add(actionColumn("Action", "Mark Missed", row -> markChipeleganyuMissed(selected, row), 120));
        page.getChildren().add(panel("Contribution Schedule", new VBox(12, actionRow(recordContributionButton(selected)), table)));
        page.getChildren().add(automaticContributionPanel(selected));
    }

    private void renderContributions() {
        page.getChildren().add(header("Contributions",
                "Central operational view for Bank Nkhonde shares and Chipeleganyu contribution obligations.",
                recordContributionButton(null)));
        List<ContributionRow> rows = contributionRows();
        FlowPane filters = new FlowPane(10, 8);
        ComboBox<String> groupFilter = combo(groupFilterOptions(), "All Groups");
        ComboBox<String> typeFilter = combo(List.of("All Types", "Bank Nkhonde", "Chipeleganyu"), "All Types");
        ComboBox<String> statusFilter = combo(List.of("All Statuses", "PAID", "PARTIAL", "PARTIALLY_PAID", "MISSED", "DUE", "OVERDUE", "UPCOMING"), "All Statuses");
        TextField periodFilter = textField("", "yyyy-MM");
        filters.getChildren().addAll(labeled("Group", groupFilter), labeled("Group Type", typeFilter), labeled("Month / Period", periodFilter), labeled("Status", statusFilter));

        TabPane tabs = new TabPane();
        tabs.getTabs().add(tab("This Month", contributionTable(filterContributionRows(rows, "This Month", groupFilter, typeFilter, statusFilter, periodFilter))));
        tabs.getTabs().add(tab("Upcoming", contributionTable(filterContributionRows(rows, "Upcoming", groupFilter, typeFilter, statusFilter, periodFilter))));
        tabs.getTabs().add(tab("Missed", contributionTable(filterContributionRows(rows, "Missed", groupFilter, typeFilter, statusFilter, periodFilter))));
        tabs.getTabs().add(tab("All Contributions", contributionTable(filterContributionRows(rows, "All", groupFilter, typeFilter, statusFilter, periodFilter))));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Runnable refreshTabs = () -> {
            List<ContributionRow> updated = filterContributionRows(contributionRows(), "This Month", groupFilter, typeFilter, statusFilter, periodFilter);
            tabs.getTabs().set(0, tab("This Month", contributionTable(updated)));
            tabs.getTabs().set(1, tab("Upcoming", contributionTable(filterContributionRows(contributionRows(), "Upcoming", groupFilter, typeFilter, statusFilter, periodFilter))));
            tabs.getTabs().set(2, tab("Missed", contributionTable(filterContributionRows(contributionRows(), "Missed", groupFilter, typeFilter, statusFilter, periodFilter))));
            tabs.getTabs().set(3, tab("All Contributions", contributionTable(filterContributionRows(contributionRows(), "All", groupFilter, typeFilter, statusFilter, periodFilter))));
        };
        groupFilter.setOnAction(event -> refreshTabs.run());
        typeFilter.setOnAction(event -> refreshTabs.run());
        statusFilter.setOnAction(event -> refreshTabs.run());
        periodFilter.textProperty().addListener((obs, old, value) -> refreshTabs.run());

        YearMonth now = YearMonth.now();
        double expected = rows.stream().filter(row -> now.toString().equals(row.period())).mapToDouble(ContributionRow::expected).sum();
        double paid = rows.stream().filter(row -> now.toString().equals(row.period())).mapToDouble(ContributionRow::paid).sum();
        double missed = rows.stream().filter(row -> "MISSED".equalsIgnoreCase(row.status())).mapToDouble(ContributionRow::expected).sum();
        page.getChildren().add(metricRow(
                metric("Expected This Month", money(expected), "All Savings Groups"),
                metric("Paid This Month", money(paid), "Posted contributions"),
                metric("Outstanding", money(Math.max(0, expected - paid)), "This month difference"),
                metric("Missed", money(missed), "Marked missed without posting")
        ));
        page.getChildren().add(panel("Filters", filters));
        page.getChildren().add(tabs);
    }

    private void renderPayouts() {
        page.getChildren().add(header("Payouts and Share-outs",
                "Manage money returned from Chipeleganyu payouts and Bank Nkhonde share-outs.",
                primaryPayoutButton(null)));
        List<PayoutRow> rows = payoutRows();
        double expectedChipe = rows.stream().filter(row -> row.type().equals("Chipeleganyu")).mapToDouble(PayoutRow::expected).sum();
        double estimatedBank = rows.stream().filter(row -> row.type().equals("Bank Nkhonde")).mapToDouble(PayoutRow::expected).sum();
        double received = rows.stream().mapToDouble(PayoutRow::actual).sum();
        double pending = rows.stream().filter(row -> !"RECEIVED".equals(row.status())).mapToDouble(PayoutRow::expected).sum();
        page.getChildren().add(metricRow(
                metric("Expected Chipeleganyu Payouts", money(expectedChipe), "Configured expected receipts"),
                metric("Estimated Bank Nkhonde Share-outs", money(estimatedBank), "Configured or net-position estimate"),
                metric("Received", money(received), "Posted incoming money"),
                metric("Pending", money(pending), "Not confirmed")
        ));
        TableView<PayoutRow> table = table(rows, 420);
        table.getColumns().add(textColumn("Group", PayoutRow::group, 170));
        table.getColumns().add(textColumn("Type", PayoutRow::type, 120));
        table.getColumns().add(textColumn("Expected Amount", row -> money(row.expected(), row.currency()), 140));
        table.getColumns().add(textColumn("Actual Amount", row -> money(row.actual(), row.currency()), 130));
        table.getColumns().add(textColumn("Expected Date", PayoutRow::expectedDate, 120));
        table.getColumns().add(textColumn("Received Date", PayoutRow::receivedDate, 120));
        table.getColumns().add(textColumn("Destination Account", PayoutRow::destinationAccount, 150));
        table.getColumns().add(textColumn("Status", PayoutRow::status, 110));
        table.getColumns().add(actionColumn("Action", "Confirm", row -> showPayoutDialog(row.profile()), 105));
        page.getChildren().add(panel("Payouts and Share-outs", table));
    }

    private void renderLedger() {
        page.getChildren().add(header("Savings Ledger & History",
                "Complete history of Savings Group financial activity.",
                exportButton("Export CSV", "csv"), exportButton("Export Excel", "xls")));
        List<LedgerRow> allRows = ledgerRows();
        TextField search = textField("", "Search");
        ComboBox<String> groupFilter = combo(groupFilterOptions(), "All Groups");
        ComboBox<String> typeFilter = combo(List.of("All Types", "Bank Nkhonde", "Chipeleganyu"), "All Types");
        ComboBox<String> txFilter = combo(List.of("All Transactions", "CONTRIBUTION", "SHARE_PURCHASE", "LOAN_RECEIVED", "LOAN_REPAYMENT", "INTEREST", "MISSED_CONTRIBUTION", "PAYOUT", "SHARE_OUT", "ADJUSTMENT", "REVERSAL"), "All Transactions");
        ComboBox<String> statusFilter = combo(List.of("All Statuses", "COMPLETED", "PAID", "PARTIAL", "MISSED", "ACTIVE", "PARTIALLY_REPAID", "OVERDUE", "RECEIVED", "PENDING"), "All Statuses");
        DatePicker start = datePicker(null);
        DatePicker end = datePicker(null);
        FlowPane filters = new FlowPane(10, 8,
                labeled("Search", search),
                labeled("Group", groupFilter),
                labeled("Group Type", typeFilter),
                labeled("Transaction Type", txFilter),
                labeled("Status", statusFilter),
                labeled("Start Date", start),
                labeled("End Date", end));
        TableView<LedgerRow> table = ledgerTable(allRows);
        Runnable apply = () -> table.setItems(FXCollections.observableArrayList(filterLedgerRows(allRows, search.getText(), groupFilter.getValue(),
                typeFilter.getValue(), txFilter.getValue(), statusFilter.getValue(), start.getValue(), end.getValue())));
        search.textProperty().addListener((obs, old, value) -> apply.run());
        groupFilter.setOnAction(event -> apply.run());
        typeFilter.setOnAction(event -> apply.run());
        txFilter.setOnAction(event -> apply.run());
        statusFilter.setOnAction(event -> apply.run());
        start.setOnAction(event -> apply.run());
        end.setOnAction(event -> apply.run());
        page.getChildren().add(panel("Filters", filters));
        page.getChildren().add(panel("Ledger", table));
    }

    private TableView<LedgerRow> ledgerTable(List<LedgerRow> rows) {
        TableView<LedgerRow> table = table(rows, 470);
        table.getColumns().add(textColumn("Date", row -> safeDate(row.date()), 105));
        table.getColumns().add(textColumn("Reference", LedgerRow::reference, 130));
        table.getColumns().add(textColumn("Group", LedgerRow::group, 160));
        table.getColumns().add(textColumn("Group Type", LedgerRow::groupType, 120));
        table.getColumns().add(textColumn("Transaction Type", LedgerRow::transactionType, 150));
        table.getColumns().add(textColumn("Description", LedgerRow::description, 230));
        table.getColumns().add(textColumn("Debit", row -> row.debit() <= 0 ? "" : money(row.debit(), row.currency()), 115));
        table.getColumns().add(textColumn("Credit", row -> row.credit() <= 0 ? "" : money(row.credit(), row.currency()), 115));
        table.getColumns().add(textColumn("Position Balance", row -> row.positionBalance() == null ? "" : money(row.positionBalance(), row.currency()), 130));
        table.getColumns().add(textColumn("Account", LedgerRow::account, 150));
        table.getColumns().add(textColumn("Status", LedgerRow::status, 110));
        table.getColumns().add(actionColumn("Action", "View Details", row -> showLedgerDetails(row), 115));
        return table;
    }

    private TableView<ContributionRow> contributionTable(List<ContributionRow> rows) {
        TableView<ContributionRow> table = table(rows, 420);
        table.getColumns().add(textColumn("Group", ContributionRow::group, 170));
        table.getColumns().add(textColumn("Type", ContributionRow::type, 120));
        table.getColumns().add(textColumn("Contribution Period", ContributionRow::period, 135));
        table.getColumns().add(textColumn("Expected", row -> money(row.expected(), row.currency()), 115));
        table.getColumns().add(textColumn("Paid", row -> money(row.paid(), row.currency()), 105));
        table.getColumns().add(textColumn("Difference", row -> money(Math.max(0, row.expected() - row.paid()), row.currency()), 115));
        table.getColumns().add(textColumn("Source Account", ContributionRow::sourceAccount, 145));
        table.getColumns().add(textColumn("Due Date", ContributionRow::dueDate, 105));
        table.getColumns().add(textColumn("Status", row -> displayStatus(row.status()), 105));
        table.getColumns().add(actionColumn("Action", "Record", row -> showContributionDialog(row.profile(), row.period(), row.expected()), 100));
        return table;
    }

    private List<ContributionRow> contributionRows() {
        Map<String, ContributionRow> rows = new LinkedHashMap<>();
        for (BankNkhondeShareRecord row : database.listBankNkhondeShares(null)) {
            SavingsGroupProfileRecord profile = profileById(row.profileId());
            if (profile == null) {
                continue;
            }
            rows.put(profile.id() + ":" + row.contributionPeriod(), new ContributionRow(profile, profile.accountName(),
                    "Bank Nkhonde", row.contributionPeriod(), row.expectedAmount(), row.paidAmount(),
                    row.sourceAccountName(), row.dueDate(), row.status(), profile.currency()));
        }
        for (ChipeleganyuContributionRecord row : database.listChipeleganyuContributions(null)) {
            SavingsGroupProfileRecord profile = profileById(row.profileId());
            if (profile == null) {
                continue;
            }
            rows.put(profile.id() + ":" + row.contributionPeriod(), new ContributionRow(profile, profile.accountName(),
                    "Chipeleganyu", row.contributionPeriod(), minorToMajor(row.expectedAmountMinor(), row.currency()),
                    minorToMajor(row.amountPaidMinor(), row.currency()), row.sourceAccountName(), row.dueDate(), row.status(), profile.currency()));
        }
        for (SavingsGroupTransactionRecord transaction : database.listSavingsGroupTransactions(null, 2000)) {
            if (!"CONTRIBUTION".equalsIgnoreCase(transaction.transactionClassification())) {
                continue;
            }
            SavingsGroupProfileRecord profile = profileByAccountId(transaction.accountId());
            if (profile == null) {
                continue;
            }
            String key = profile.id() + ":" + safe(transaction.contributionPeriod());
            rows.putIfAbsent(key, new ContributionRow(profile, profile.accountName(), displayType(profile.groupType()),
                    safe(transaction.contributionPeriod()), transaction.amount(), transaction.amount(),
                    transaction.counterAccountName(), transaction.transactionDate(), transaction.status(), profile.currency()));
        }
        return rows.values().stream()
                .sorted(Comparator.comparing(ContributionRow::period, Comparator.nullsLast(String::compareTo)).reversed())
                .toList();
    }

    private List<ContributionRow> filterContributionRows(List<ContributionRow> rows, String tab, ComboBox<String> groupFilter,
                                                         ComboBox<String> typeFilter, ComboBox<String> statusFilter, TextField periodFilter) {
        YearMonth now = YearMonth.now();
        return rows.stream()
                .filter(row -> matchesGroup(row.group(), groupFilter.getValue()))
                .filter(row -> matchesOption(row.type(), typeFilter.getValue(), "All Types"))
                .filter(row -> matchesOption(row.status(), statusFilter.getValue(), "All Statuses"))
                .filter(row -> clean(periodFilter.getText()).isBlank() || row.period().contains(clean(periodFilter.getText())))
                .filter(row -> switch (tab) {
                    case "This Month" -> now.toString().equals(row.period());
                    case "Upcoming" -> Set.of("UPCOMING", "DUE", "OVERDUE").contains(safe(row.status()).toUpperCase(Locale.ENGLISH));
                    case "Missed" -> "MISSED".equalsIgnoreCase(row.status());
                    default -> true;
                })
                .toList();
    }

    private List<PayoutRow> payoutRows() {
        List<PayoutRow> rows = new ArrayList<>();
        for (SavingsGroupProfileRecord profile : profiles) {
            double actual = profile.amountReceivedBack() + profile.profitOrBonusReceived();
            double expected = profile.expectedPayoutAmount();
            if (isBankNkhonde(profile)) {
                double outstanding = database.listBankNkhondeLoans(profile.id()).stream().mapToDouble(BankNkhondeLoanRecord::balance).sum();
                expected = estimatedBankShareOut(profile, outstanding);
            }
            rows.add(new PayoutRow(profile, profile.accountName(), displayType(profile.groupType()), expected, actual,
                    profile.expectedPayoutDate(), actual > 0 ? "Posted" : "", "", actual > 0 ? "RECEIVED" : "PENDING", profile.currency()));
        }
        return rows;
    }

    private List<LedgerRow> ledgerRows() {
        List<LedgerRow> rows = new ArrayList<>();
        for (SavingsGroupTransactionRecord row : database.listSavingsGroupTransactions(null, 3000)) {
            SavingsGroupProfileRecord profile = profileByAccountId(row.accountId());
            String groupType = profile == null ? row.groupType() : profile.groupType();
            boolean debit = "CONTRIBUTION".equalsIgnoreCase(row.transactionClassification());
            String type = switch (safe(row.transactionClassification())) {
                case "CONTRIBUTION" -> isBankType(groupType) ? "SHARE_PURCHASE" : "CONTRIBUTION";
                case "ORIGINAL_SAVINGS_RETURN" -> isBankType(groupType) ? "SHARE_OUT" : "PAYOUT";
                case "PROFIT", "BONUS" -> isBankType(groupType) ? "SHARE_OUT" : "PAYOUT";
                default -> row.transactionClassification();
            };
            rows.add(new LedgerRow(parseDate(row.transactionDate()), safe(row.referenceNumber()), row.accountName(),
                    displayType(groupType), type, safe(row.notes()), debit ? row.amount() : 0,
                    debit ? 0 : row.amount(), profile == null ? null : profile.currentContributionBalance(),
                    row.counterAccountName(), row.status(), profile == null ? baseCurrencyCode() : profile.currency(),
                    "Transaction metadata #" + row.id() + optionalSuffix("; Account transaction #", row.transactionId())));
        }
        for (ChipeleganyuContributionRecord row : database.listChipeleganyuContributions(null)) {
            if (!Set.of("MISSED", "FAILED_AUTOMATIC_DEDUCTION").contains(safe(row.status()).toUpperCase(Locale.ENGLISH))) {
                continue;
            }
            rows.add(new LedgerRow(parseDate(row.confirmationDate(), parseDate(row.dueDate())), "CHIPE-" + row.id(),
                    row.accountName(), "Chipeleganyu", "MISSED_CONTRIBUTION",
                    "Chipeleganyu contribution " + row.contributionPeriod() + " was not paid.",
                    0, 0, null, row.sourceAccountName(), row.status(), row.currency(), safe(row.notes())));
        }
        for (BankNkhondeShareRecord row : database.listBankNkhondeShares(null)) {
            if (!"MISSED".equalsIgnoreCase(row.status())) {
                continue;
            }
            rows.add(new LedgerRow(parseDate(row.confirmationDate(), parseDate(row.dueDate())), "BN-SHARE-" + row.id(),
                    row.accountName(), "Bank Nkhonde", "MISSED_CONTRIBUTION",
                    "Bank Nkhonde share period " + row.contributionPeriod() + " was missed.",
                    0, 0, null, row.sourceAccountName(), row.status(), row.currency(), safe(row.notes())));
        }
        for (BankNkhondeLoanRecord loan : database.listBankNkhondeLoans(null)) {
            rows.add(new LedgerRow(loan.loanDate(), safe(loan.referenceNumber()), loan.accountName(), "Bank Nkhonde",
                    "LOAN_RECEIVED", "Bank Nkhonde loan received.", 0, loan.principalAmount(), loan.balance(),
                    loan.receivingAccountName(), loan.status(), loan.currency(), safe(loan.notes())));
        }
        for (BankNkhondeRepaymentRecord repayment : database.listBankNkhondeRepayments(null)) {
            SavingsGroupProfileRecord profile = profileById(repayment.profileId());
            String currency = profile == null ? baseCurrencyCode() : profile.currency();
            rows.add(new LedgerRow(repayment.repaymentDate(), safe(repayment.referenceNumber()), profile == null ? "" : profile.accountName(),
                    "Bank Nkhonde", "LOAN_REPAYMENT", "Bank Nkhonde loan repayment.",
                    repayment.totalPayment(), 0, repayment.remainingBalance(), repayment.sourceAccountName(),
                    repayment.status(), currency, safe(repayment.notes())));
        }
        rows.sort(Comparator.comparing(LedgerRow::date, Comparator.nullsLast(LocalDate::compareTo)).reversed());
        return rows;
    }

    private List<LedgerRow> filterLedgerRows(List<LedgerRow> rows, String search, String group, String groupType,
                                             String txType, String status, LocalDate start, LocalDate end) {
        String needle = clean(search).toLowerCase(Locale.ENGLISH);
        return rows.stream()
                .filter(row -> needle.isBlank() || (row.group() + " " + row.reference() + " " + row.description()).toLowerCase(Locale.ENGLISH).contains(needle))
                .filter(row -> matchesGroup(row.group(), group))
                .filter(row -> matchesOption(row.groupType(), groupType, "All Types"))
                .filter(row -> matchesOption(row.transactionType(), txType, "All Transactions"))
                .filter(row -> matchesOption(row.status(), status, "All Statuses"))
                .filter(row -> start == null || row.date() == null || !row.date().isBefore(start))
                .filter(row -> end == null || row.date() == null || !row.date().isAfter(end))
                .toList();
    }

    private VBox obligationList() {
        VBox list = new VBox(8);
        list.setMaxWidth(Double.MAX_VALUE);
        List<ContributionRow> obligations = contributionRows().stream()
                .filter(row -> Set.of("DUE", "OVERDUE", "MISSED", "UPCOMING").contains(safe(row.status()).toUpperCase(Locale.ENGLISH)))
                .limit(10)
                .toList();
        if (obligations.isEmpty()) {
            list.getChildren().add(emptyText("No upcoming Savings Group obligations."));
            return list;
        }
        for (ContributionRow row : obligations) {
            list.getChildren().add(obligationRow(row.group(), row.type(), row.period(), money(Math.max(0, row.expected() - row.paid()), row.currency()), row.status()));
        }
        return list;
    }

    private Node obligationRow(String group, String type, String period, String amount, String status) {
        Label title = new Label(group + " - " + period);
        title.getStyleClass().add("savings-row-title");
        Label subtitle = new Label(type + " | " + amount);
        subtitle.getStyleClass().add("form-note");
        Label badge = statusBadge(status);
        HBox row = new HBox(12, new VBox(3, title, subtitle), spacer(), badge);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("savings-list-row");
        return row;
    }

    private TableView<SavingsGroupTransactionRecord> recentActivityTable(int limit) {
        return recentActivityTable(null, limit);
    }

    private TableView<SavingsGroupTransactionRecord> recentActivityTable(Integer profileId, int limit) {
        List<SavingsGroupTransactionRecord> rows = database.listSavingsGroupTransactions(profileId, limit);
        TableView<SavingsGroupTransactionRecord> table = table(rows, 240);
        table.getColumns().add(textColumn("Date", SavingsGroupTransactionRecord::transactionDate, 105));
        table.getColumns().add(textColumn("Group", SavingsGroupTransactionRecord::accountName, 160));
        table.getColumns().add(textColumn("Activity", row -> displayClassification(row.transactionClassification()), 150));
        table.getColumns().add(textColumn("Amount", row -> money(row.amount(), profileByAccountCurrency(row.accountId())), 120));
        table.getColumns().add(textColumn("Status", row -> displayStatus(row.status()), 100));
        return table;
    }

    private Node automaticContributionPanel(SavingsGroupProfileRecord profile) {
        SavingsGroupRuleRecord rules = database.getSavingsGroupRules(profile.id());
        CheckBox enabled = new CheckBox("Automatic Contribution");
        enabled.setSelected(rules.automaticContributionEnabled());
        enabled.setDisable(true);
        GridPane grid = formGrid();
        grid.add(detailRow("Automatic Contribution", rules.automaticContributionEnabled() ? "ON" : "OFF"), 0, 0);
        grid.add(detailRow("Source Account", accountName(profile.sourceAccountId())), 1, 0);
        grid.add(detailRow("Contribution Amount", money(profile.expectedContributionAmount(), profile.currency())), 2, 0);
        grid.add(detailRow("Recurring Day", safe(profile.contributionDay())), 0, 1);
        grid.add(detailRow("Start Date", safe(profile.actualStartDate())), 1, 1);
        grid.add(detailRow("End Date", safe(profile.expectedCycleEndDate())), 2, 1);
        Label note = new Label("Automatic deduction processing is not installed in this build; failed automatic deduction states are supported by the data layer and remain visible in the schedule.");
        note.setWrapText(true);
        note.getStyleClass().add("form-note");
        return panel("Automatic Contribution", new VBox(12, enabled, grid, note));
    }

    private FlowPane periodStrip(List<ChipeleganyuContributionRecord> schedule) {
        FlowPane strip = new FlowPane(6, 6);
        for (ChipeleganyuContributionRecord row : schedule) {
            Label item = new Label(row.contributionPeriod());
            item.getStyleClass().add("period-chip");
            item.getStyleClass().add("period-" + safe(row.status()).toLowerCase(Locale.ENGLISH).replace('_', '-'));
            strip.getChildren().add(item);
        }
        return strip;
    }

    private void showContributionDialog(SavingsGroupProfileRecord suggestedProfile, String suggestedPeriod, double suggestedAmount) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record Contribution");
        dialog.setHeaderText("Record a real Savings Group contribution from a PFMIS account.");
        ButtonType save = new ButtonType("Record Contribution", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        ComboBox<SavingsGroupProfileRecord> profileBox = profileCombo(profiles, suggestedProfile);
        TextField period = textField(safe(suggestedPeriod).isBlank() ? YearMonth.now().toString() : suggestedPeriod, "yyyy-MM");
        TextField amount = textField(suggestedAmount > 0 ? amountText(suggestedAmount) : "", "0.00");
        DatePicker date = datePicker(LocalDate.now());
        ComboBox<Account> source = accountCombo(suggestedProfile == null ? null : suggestedProfile.sourceAccountId());
        TextField method = textField("", "Payment method");
        TextField reference = textField("", "Reference");
        TextArea notes = textArea("", "Notes");

        GridPane grid = formGrid();
        addField(grid, 0, 0, "Savings Group", profileBox);
        addField(grid, 1, 0, "Contribution Period", period);
        addField(grid, 2, 0, "Amount", amount);
        addField(grid, 0, 1, "Payment Date", date);
        addField(grid, 1, 1, "Source Account", source);
        addField(grid, 2, 1, "Payment Method", method);
        addField(grid, 0, 2, "Reference", reference);
        addField(grid, 0, 3, "Notes", notes, 3);
        dialog.getDialogPane().setContent(grid);
        Node saveNode = dialog.getDialogPane().lookupButton(save);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                SavingsGroupProfileRecord profile = requireValue(profileBox.getValue(), "Select a Savings Group.");
                Account account = requireValue(source.getValue(), "Select a source account.");
                database.recordSavingsGroupContribution(new SavingsGroupContributionCommand(
                        profile.id(),
                        requireValue(date.getValue(), "Payment date is required."),
                        requireYearMonth(period.getText()).toString(),
                        parseAmount(amount.getText(), "Contribution amount"),
                        account.getId(),
                        clean(method.getText()),
                        clean(reference.getText()),
                        clean(notes.getText()),
                        "",
                        false
                ));
                DataRefreshBus.notifyDataChanged();
                render();
            } catch (RuntimeException exception) {
                event.consume();
                showProblem("Contribution was not recorded", exception);
            }
        });
        dialog.showAndWait();
    }

    private void markChipeleganyuMissed(SavingsGroupProfileRecord profile, ChipeleganyuContributionRecord row) {
        if (row.amountPaidMinor() > 0 || row.transactionId() != null) {
            showMessage("Cannot mark missed", "This period already has posted money movement. Reverse the posted transaction before marking it missed.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Mark Chipeleganyu Month Missed");
        dialog.setHeaderText("This records that no contribution was made. No account transaction will be created.");
        ButtonType save = new ButtonType("Mark Missed", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ComboBox<String> reason = combo(List.of("PAYMENT_NOT_MADE", "INSUFFICIENT_FUNDS", "GROUP_ALLOWED_SKIP", "USER_ABSENT", "PAYMENT_DEFERRED", "OTHER"), "PAYMENT_NOT_MADE");
        CheckBox mayPayLater = new CheckBox("May be paid later");
        mayPayLater.setSelected(true);
        TextArea notes = textArea("", "Notes");
        GridPane grid = formGrid();
        grid.add(detailRow("Contribution Period", row.contributionPeriod()), 0, 0);
        grid.add(detailRow("Expected Amount", moneyMinor(row.expectedAmountMinor(), row.currency())), 1, 0);
        addField(grid, 0, 1, "Reason", reason);
        addField(grid, 1, 1, "Later Settlement", mayPayLater);
        addField(grid, 0, 2, "Notes", notes, 2);
        dialog.getDialogPane().setContent(grid);
        Node saveNode = dialog.getDialogPane().lookupButton(save);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                database.markChipeleganyuContributionMissed(new MarkChipeleganyuMissedContributionCommand(
                        profile.id(),
                        row.contributionPeriod(),
                        parseDate(row.dueDate(), LocalDate.now()),
                        row.expectedAmountMinor(),
                        reason.getValue(),
                        mayPayLater.isSelected(),
                        clean(notes.getText()),
                        LocalDate.now()
                ));
                DataRefreshBus.notifyDataChanged();
                render();
            } catch (RuntimeException exception) {
                event.consume();
                showProblem("Could not mark contribution missed", exception);
            }
        });
        dialog.showAndWait();
    }

    private void markBankShareMissed(SavingsGroupProfileRecord profile, BankNkhondeShareRecord row) {
        if (row.paidAmount() > 0 || row.transactionId() != null) {
            showMessage("Cannot mark missed", "This share period already has posted money movement. Reverse the posted transaction before marking it missed.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Mark Bank Nkhonde Share Missed");
        dialog.setHeaderText("This keeps the missed share period visible without changing any account balance.");
        ButtonType save = new ButtonType("Mark Missed", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextArea notes = textArea("", "Notes");
        GridPane grid = formGrid();
        grid.add(detailRow("Share Period", row.contributionPeriod()), 0, 0);
        grid.add(detailRow("Expected Amount", money(row.expectedAmount(), row.currency())), 1, 0);
        addField(grid, 0, 1, "Notes", notes, 2);
        dialog.getDialogPane().setContent(grid);
        Node saveNode = dialog.getDialogPane().lookupButton(save);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                database.markBankNkhondeShareMissed(new MarkBankNkhondeShareMissedCommand(
                        profile.id(),
                        row.contributionPeriod(),
                        parseDate(row.dueDate(), LocalDate.now()),
                        row.expectedAmount(),
                        clean(notes.getText()),
                        LocalDate.now()
                ));
                DataRefreshBus.notifyDataChanged();
                render();
            } catch (RuntimeException exception) {
                event.consume();
                showProblem("Could not mark share period missed", exception);
            }
        });
        dialog.showAndWait();
    }

    private void showLoanDialog(SavingsGroupProfileRecord suggestedProfile) {
        SavingsGroupProfileRecord profile = suggestedProfile == null ? selectedProfile(profiles.stream().filter(this::isBankNkhonde).toList(), selectedBankProfileId) : suggestedProfile;
        if (profile == null) {
            showMessage("Record Loan", "Add or select a Bank Nkhonde group first.");
            return;
        }
        SavingsGroupRuleRecord rules = database.getSavingsGroupRules(profile.id());
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record Bank Nkhonde Loan");
        dialog.setHeaderText("Record money received from a Bank Nkhonde group.");
        ButtonType save = new ButtonType("Record Loan", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        TextField principal = textField("", "0.00");
        TextField interest = textField(amountText(rules.loanInterestRate()), "0.00");
        DatePicker loanDate = datePicker(LocalDate.now());
        DatePicker dueDate = datePicker(null);
        ComboBox<Account> receiving = accountCombo(null);
        TextField reference = textField("", "Reference");
        TextArea notes = textArea("", "Notes");
        GridPane grid = formGrid();
        addField(grid, 0, 0, "Principal", principal);
        addField(grid, 1, 0, "Interest Rate %", interest);
        addField(grid, 2, 0, "Receiving Account", receiving);
        addField(grid, 0, 1, "Loan Date", loanDate);
        addField(grid, 1, 1, "Due Date", dueDate);
        addField(grid, 2, 1, "Reference", reference);
        addField(grid, 0, 2, "Notes", notes, 3);
        dialog.getDialogPane().setContent(grid);
        Node saveNode = dialog.getDialogPane().lookupButton(save);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                Account account = requireValue(receiving.getValue(), "Select the account that received the loan money.");
                database.recordBankNkhondeLoan(new BankNkhondeLoanCommand(
                        profile.id(),
                        account.getId(),
                        parseAmount(principal.getText(), "Loan principal"),
                        parseOptionalAmount(interest.getText(), "Interest rate"),
                        requireValue(loanDate.getValue(), "Loan date is required."),
                        dueDate.getValue(),
                        clean(reference.getText()),
                        clean(notes.getText()),
                        "",
                        false
                ));
                DataRefreshBus.notifyDataChanged();
                render();
            } catch (RuntimeException exception) {
                event.consume();
                showProblem("Loan was not recorded", exception);
            }
        });
        dialog.showAndWait();
    }

    private void showRepaymentDialog(SavingsGroupProfileRecord profile, BankNkhondeLoanRecord suggestedLoan) {
        List<BankNkhondeLoanRecord> loans = database.listBankNkhondeLoans(profile.id()).stream()
                .filter(loan -> loan.balance() > 0 && !"CANCELLED".equalsIgnoreCase(loan.status()))
                .toList();
        if (loans.isEmpty()) {
            showMessage("Record Repayment", "There are no active Bank Nkhonde loans to repay.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Record Bank Nkhonde Repayment");
        dialog.setHeaderText("Record repayment money leaving a PFMIS account.");
        ButtonType save = new ButtonType("Record Repayment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        ComboBox<BankNkhondeLoanRecord> loanBox = new ComboBox<>(FXCollections.observableArrayList(loans));
        loanBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BankNkhondeLoanRecord loan) {
                return loan == null ? "" : safe(loan.referenceNumber()) + " - " + money(loan.balance(), loan.currency()) + " balance";
            }

            @Override
            public BankNkhondeLoanRecord fromString(String value) {
                return null;
            }
        });
        loanBox.setValue(suggestedLoan == null ? loans.get(0) : suggestedLoan);
        TextField principal = textField("", "0.00");
        TextField interest = textField("", "0.00");
        DatePicker date = datePicker(LocalDate.now());
        ComboBox<Account> source = accountCombo(profile.sourceAccountId());
        TextField method = textField("", "Payment method");
        TextField reference = textField("", "Reference");
        TextArea notes = textArea("", "Notes");
        GridPane grid = formGrid();
        addField(grid, 0, 0, "Loan", loanBox);
        addField(grid, 1, 0, "Principal Component", principal);
        addField(grid, 2, 0, "Interest Component", interest);
        addField(grid, 0, 1, "Repayment Date", date);
        addField(grid, 1, 1, "Source Account", source);
        addField(grid, 2, 1, "Payment Method", method);
        addField(grid, 0, 2, "Reference", reference);
        addField(grid, 0, 3, "Notes", notes, 3);
        dialog.getDialogPane().setContent(grid);
        Node saveNode = dialog.getDialogPane().lookupButton(save);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                BankNkhondeLoanRecord loan = requireValue(loanBox.getValue(), "Select a loan.");
                Account account = requireValue(source.getValue(), "Select a source account.");
                database.recordBankNkhondeRepayment(new BankNkhondeRepaymentCommand(
                        loan.id(),
                        account.getId(),
                        requireValue(date.getValue(), "Repayment date is required."),
                        parseOptionalAmount(principal.getText(), "Principal component"),
                        parseOptionalAmount(interest.getText(), "Interest component"),
                        clean(method.getText()),
                        clean(reference.getText()),
                        clean(notes.getText()),
                        "",
                        false
                ));
                DataRefreshBus.notifyDataChanged();
                render();
            } catch (RuntimeException exception) {
                event.consume();
                showProblem("Repayment was not recorded", exception);
            }
        });
        dialog.showAndWait();
    }

    private void showPayoutDialog(SavingsGroupProfileRecord suggestedProfile) {
        SavingsGroupProfileRecord profile = suggestedProfile == null ? selectedProfile(profiles, null) : suggestedProfile;
        if (profile == null) {
            showMessage("Confirm Payout", "Add or select a Savings Group first.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        boolean bank = isBankNkhonde(profile);
        dialog.setTitle(bank ? "Confirm Share-out" : "Confirm Payout");
        dialog.setHeaderText("Confirm the actual money received into a PFMIS account.");
        ButtonType save = new ButtonType(bank ? "Confirm Share-out" : "Confirm Payout", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        double expected = bank ? estimatedBankShareOut(profile, database.listBankNkhondeLoans(profile.id()).stream().mapToDouble(BankNkhondeLoanRecord::balance).sum()) : profile.expectedPayoutAmount();
        TextField original = textField(amountText(Math.max(0, Math.min(expected, profile.currentContributionBalance()))), "0.00");
        TextField profit = textField("", "0.00");
        TextField bonus = textField("", "0.00");
        TextField deduction = textField("", "0.00");
        DatePicker date = datePicker(LocalDate.now());
        ComboBox<Account> receiving = accountCombo(null);
        TextField reference = textField("", "Reference");
        TextArea notes = textArea("", "Notes");
        GridPane grid = formGrid();
        grid.add(detailRow("Calculated / Expected Amount", money(expected, profile.currency())), 0, 0);
        addField(grid, 1, 0, "Original Savings Received", original);
        addField(grid, 2, 0, "Profit / Interest", profit);
        addField(grid, 0, 1, "Bonus", bonus);
        addField(grid, 1, 1, "Deductions", deduction);
        addField(grid, 2, 1, "Receiving Account", receiving);
        addField(grid, 0, 2, "Date Received", date);
        addField(grid, 1, 2, "Reference", reference);
        addField(grid, 0, 3, "Notes", notes, 3);
        dialog.getDialogPane().setContent(grid);
        Node saveNode = dialog.getDialogPane().lookupButton(save);
        saveNode.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                Account account = requireValue(receiving.getValue(), "Select the receiving financial account.");
                database.recordSavingsGroupPayout(new SavingsGroupPayoutCommand(
                        profile.id(),
                        requireValue(date.getValue(), "Date received is required."),
                        parseOptionalAmount(original.getText(), "Original savings"),
                        parseOptionalAmount(profit.getText(), "Profit or interest"),
                        parseOptionalAmount(bonus.getText(), "Bonus"),
                        parseOptionalAmount(deduction.getText(), "Deduction"),
                        account.getId(),
                        clean(reference.getText()),
                        clean(notes.getText()),
                        "",
                        false
                ));
                DataRefreshBus.notifyDataChanged();
                render();
            } catch (RuntimeException exception) {
                event.consume();
                showProblem((bank ? "Share-out" : "Payout") + " was not confirmed", exception);
            }
        });
        dialog.showAndWait();
    }

    private void exportLedger(String extension) {
        List<LedgerRow> rows = ledgerRows();
        String delimiter = extension.equals("csv") ? "," : "\t";
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(delimiter, List.of("Date", "Reference", "Group", "Group Type", "Transaction Type", "Description", "Debit", "Credit", "Position Balance", "Account", "Status"))).append(System.lineSeparator());
        for (LedgerRow row : rows) {
            builder.append(csv(row.date() == null ? "" : row.date().toString(), delimiter)).append(delimiter)
                    .append(csv(row.reference(), delimiter)).append(delimiter)
                    .append(csv(row.group(), delimiter)).append(delimiter)
                    .append(csv(row.groupType(), delimiter)).append(delimiter)
                    .append(csv(row.transactionType(), delimiter)).append(delimiter)
                    .append(csv(row.description(), delimiter)).append(delimiter)
                    .append(row.debit()).append(delimiter)
                    .append(row.credit()).append(delimiter)
                    .append(row.positionBalance() == null ? "" : row.positionBalance()).append(delimiter)
                    .append(csv(row.account(), delimiter)).append(delimiter)
                    .append(csv(row.status(), delimiter)).append(System.lineSeparator());
        }
        try {
            Path file = ExportPathService.writeTextExport(
                    ExportPathService.defaultFileName("Savings Ledger", extension),
                    builder.toString()
            );
            showMessage("Export complete", ExportPathService.successMessage(file));
        } catch (IOException exception) {
            showProblem("Savings ledger export failed", exception);
        }
    }

    private String csv(String value, String delimiter) {
        String safe = safe(value);
        if ("\t".equals(delimiter)) {
            return safe.replace('\t', ' ');
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private Button recordContributionButton(SavingsGroupProfileRecord profile) {
        Button button = primaryButton("+ Record Contribution");
        button.setOnAction(event -> showContributionDialog(profile, YearMonth.now().toString(), profile == null ? 0 : profile.expectedContributionAmount()));
        return button;
    }

    private Button recordShareButton(SavingsGroupProfileRecord profile) {
        Button button = primaryButton("+ Record Share Purchase");
        button.setOnAction(event -> showContributionDialog(profile == null ? selectedProfile(profiles.stream().filter(this::isBankNkhonde).toList(), selectedBankProfileId) : profile,
                YearMonth.now().toString(), profile == null ? 0 : profile.expectedContributionAmount()));
        return button;
    }

    private Button recordLoanButton(SavingsGroupProfileRecord profile) {
        Button button = primaryButton("+ Record Loan");
        button.setOnAction(event -> showLoanDialog(profile));
        return button;
    }

    private Button primaryPayoutButton(SavingsGroupProfileRecord profile) {
        Button button = primaryButton(profile != null && isBankNkhonde(profile) ? "Confirm Share-out" : "Confirm Payout");
        button.setOnAction(event -> showPayoutDialog(profile));
        return button;
    }

    private Button exportButton(String text, String extension) {
        Button button = secondaryButton(text);
        button.setOnAction(event -> exportLedger(extension));
        return button;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private HBox header(String title, String subtitle, Node... actions) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("savings-page-title");
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.setWrapText(true);
        subtitleLabel.getStyleClass().add("form-note");
        VBox text = new VBox(4, titleLabel, subtitleLabel);
        HBox row = new HBox(16, text, spacer());
        row.setAlignment(Pos.CENTER_LEFT);
        for (Node action : actions) {
            row.getChildren().add(action);
        }
        return row;
    }

    private FlowPane metricRow(Node... cards) {
        FlowPane pane = new FlowPane(14, 14);
        pane.getChildren().addAll(cards);
        pane.setPrefWrapLength(960);
        pane.setMaxWidth(Double.MAX_VALUE);
        return pane;
    }

    private VBox metric(String title, String value, String note) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("metric-value");
        Label noteLabel = new Label(note);
        noteLabel.setWrapText(true);
        noteLabel.getStyleClass().add("metric-note");
        VBox card = new VBox(6, titleLabel, valueLabel, noteLabel);
        card.getStyleClass().add("savings-metric-card");
        return card;
    }

    private VBox panel(String title, Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("savings-panel-title");
        VBox box = new VBox(12, label, content);
        box.getStyleClass().add("savings-panel");
        box.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private VBox selectorPanel(String title, ComboBox<SavingsGroupProfileRecord> selector, SavingsGroupProfileRecord selected) {
        Label label = new Label(title);
        label.getStyleClass().add("field-label");
        selector.setMaxWidth(360);
        Label status = statusBadge(selected.status());
        HBox row = new HBox(12, selector, status, spacer(), new Label("Start: " + safe(selected.actualStartDate()) + " | End: " + safe(selected.expectedCycleEndDate())));
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().get(row.getChildren().size() - 1).getStyleClass().add("form-note");
        return panel(title, new VBox(8, label, row));
    }

    private VBox statusPanel(SavingsGroupProfileRecord profile) {
        LocalDate end = parseDate(profile.expectedCycleEndDate());
        long remaining = end == null ? 0 : Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.now().atDay(1), YearMonth.from(end).atDay(1)) + 1);
        GridPane grid = formGrid();
        grid.add(detailRow("Start Date", safe(profile.actualStartDate())), 0, 0);
        grid.add(detailRow("End Date", safe(profile.expectedCycleEndDate())), 1, 0);
        grid.add(detailRow("Months / Periods Remaining", end == null ? "Not set" : String.valueOf(remaining)), 2, 0);
        grid.add(detailRow("Group Status", displayStatus(profile.status())), 3, 0);
        return panel("Group Status", grid);
    }

    private Node emptyState(String message, String buttonText, Runnable action) {
        Label text = new Label(message);
        text.getStyleClass().add("savings-empty-title");
        Button button = primaryButton(buttonText);
        button.setOnAction(event -> action.run());
        VBox box = new VBox(14, text, button);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("savings-empty-state");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Label emptyText(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("form-note");
        label.setWrapText(true);
        return label;
    }

    private Label statusBadge(String status) {
        Label label = new Label(displayStatus(status));
        label.getStyleClass().add("status-badge");
        label.getStyleClass().add("status-" + safe(status).toLowerCase(Locale.ENGLISH).replace('_', '-'));
        return label;
    }

    private HBox actionRow(Node... nodes) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.getChildren().add(spacer());
        row.getChildren().addAll(nodes);
        return row;
    }

    private Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(10);
        grid.setMaxWidth(Double.MAX_VALUE);
        return grid;
    }

    private void addField(GridPane grid, int column, int row, String label, Node input) {
        addField(grid, column, row, label, input, 1);
    }

    private void addField(GridPane grid, int column, int row, String label, Node input, int colspan) {
        VBox box = labeled(label, input);
        grid.add(box, column, row, colspan, 1);
    }

    private VBox labeled(String label, Node input) {
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        VBox box = new VBox(5, fieldLabel, input);
        box.setMinWidth(180);
        return box;
    }

    private HBox detailRow(String label, String value) {
        Label title = new Label(label);
        title.getStyleClass().add("metric-title");
        Label body = new Label(safe(value).isBlank() ? "-" : value);
        body.setWrapText(true);
        body.getStyleClass().add("savings-detail-value");
        VBox stack = new VBox(4, title, body);
        HBox wrapper = new HBox(stack);
        wrapper.getStyleClass().add("savings-detail-cell");
        wrapper.setMaxWidth(Double.MAX_VALUE);
        return wrapper;
    }

    private HBox stepper() {
        HBox row = new HBox(8);
        row.getStyleClass().add("wizard-stepper");
        row.getChildren().add(stepLabel(1, "Group Type"));
        row.getChildren().add(stepLabel(2, "Group Details"));
        row.getChildren().add(stepLabel(3, "Financial Rules"));
        row.getChildren().add(stepLabel(4, "Review & Save"));
        return row;
    }

    private Label stepLabel(int step, String text) {
        Label label = new Label(step + ". " + text);
        label.getStyleClass().add(step == wizard.step ? "wizard-step-active" : "wizard-step");
        return label;
    }

    private <T> TableView<T> table(List<T> rows, double height) {
        TableView<T> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.setPrefHeight(height);
        table.setMinHeight(Math.min(180, height));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        return table;
    }

    private <T> TableColumn<T, String> textColumn(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(safe(value.apply(cell.getValue()))));
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, Void> actionColumn(String title, String buttonText, java.util.function.Consumer<T> action, double width) {
        TableColumn<T, Void> column = new TableColumn<>(title);
        column.setPrefWidth(width);
        column.setCellFactory(table -> new TableCell<>() {
            private final Button button = secondaryButton(buttonText);

            {
                button.setOnAction(event -> {
                    T row = getTableView().getItems().get(getIndex());
                    action.accept(row);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                } else {
                    setGraphic(button);
                }
            }
        });
        return column;
    }

    private Tab tab(String title, Node content) {
        Tab tab = new Tab(title, content);
        tab.setClosable(false);
        return tab;
    }

    private TextField textField(String value, String prompt) {
        TextField field = new TextField(safe(value));
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private TextArea textArea(String value, String prompt) {
        TextArea area = new TextArea(safe(value));
        area.setPromptText(prompt);
        area.setWrapText(true);
        area.setPrefRowCount(3);
        area.setMaxWidth(Double.MAX_VALUE);
        return area;
    }

    private DatePicker datePicker(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.setMaxWidth(Double.MAX_VALUE);
        return picker;
    }

    private <T> ComboBox<T> combo(List<T> values, T selected) {
        ComboBox<T> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setValue(selected);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private ComboBox<Account> accountCombo(Integer selectedAccountId) {
        ComboBox<Account> box = new ComboBox<>(FXCollections.observableArrayList(activeAccounts));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.getAccountName();
            }

            @Override
            public Account fromString(String value) {
                return activeAccounts.stream().filter(account -> account.getAccountName().equalsIgnoreCase(value)).findFirst().orElse(null);
            }
        });
        if (selectedAccountId != null) {
            activeAccounts.stream().filter(account -> account.getId() == selectedAccountId).findFirst().ifPresent(box::setValue);
        }
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private ComboBox<SavingsGroupProfileRecord> profileCombo(List<SavingsGroupProfileRecord> values, SavingsGroupProfileRecord selected) {
        ComboBox<SavingsGroupProfileRecord> box = new ComboBox<>(FXCollections.observableArrayList(values));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(SavingsGroupProfileRecord profile) {
                return profile == null ? "" : profile.accountName();
            }

            @Override
            public SavingsGroupProfileRecord fromString(String value) {
                return values.stream().filter(profile -> profile.accountName().equalsIgnoreCase(value)).findFirst().orElse(null);
            }
        });
        box.setValue(selected);
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private List<String> groupFilterOptions() {
        List<String> options = new ArrayList<>();
        options.add("All Groups");
        options.addAll(profiles.stream().map(SavingsGroupProfileRecord::accountName).toList());
        return options;
    }

    private void tryBuildChipeleganyuSchedule(SavingsGroupProfileRecord profile) {
        try {
            if ("Monthly".equalsIgnoreCase(profile.contributionFrequency())) {
                database.ensureChipeleganyuContributionSchedule(profile.id());
            }
        } catch (RuntimeException ignored) {
            // The page can still show profile data when dates are incomplete.
        }
    }

    private void tryBuildBankSchedule(SavingsGroupProfileRecord profile) {
        try {
            if ("Monthly".equalsIgnoreCase(profile.contributionFrequency())) {
                database.ensureBankNkhondeShareSchedule(profile.id());
            }
        } catch (RuntimeException ignored) {
            // The page can still show manually recorded shares when dates are incomplete.
        }
    }

    private SavingsGroupProfileRecord selectedProfile(List<SavingsGroupProfileRecord> values, Integer selectedId) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        if (selectedId != null) {
            Optional<SavingsGroupProfileRecord> selected = values.stream().filter(profile -> profile.id() == selectedId).findFirst();
            if (selected.isPresent()) {
                return selected.get();
            }
        }
        return values.get(0);
    }

    private SavingsGroupProfileRecord profileById(int profileId) {
        return profiles.stream().filter(profile -> profile.id() == profileId).findFirst().orElse(null);
    }

    private SavingsGroupProfileRecord profileByAccountId(int accountId) {
        return profiles.stream().filter(profile -> profile.accountId() == accountId).findFirst().orElse(null);
    }

    private String profileByAccountCurrency(int accountId) {
        SavingsGroupProfileRecord profile = profileByAccountId(accountId);
        return profile == null ? baseCurrencyCode() : profile.currency();
    }

    private boolean isBankNkhonde(SavingsGroupProfileRecord profile) {
        return profile != null && isBankType(profile.groupType());
    }

    private boolean isBankType(String type) {
        return "BANK NKHONDE".equals(safe(type).trim().toUpperCase(Locale.ENGLISH).replace('_', ' '));
    }

    private boolean isChipeleganyu(SavingsGroupProfileRecord profile) {
        String clean = profile == null ? "" : safe(profile.groupType()).trim().toUpperCase(Locale.ENGLISH).replace('_', ' ');
        return "CHIPELEGANYU".equals(clean) || "ZIPELEGANYU".equals(clean);
    }

    private boolean isWizardBankNkhonde() {
        return "Bank Nkhonde".equalsIgnoreCase(wizard.groupType);
    }

    private boolean isWizardChipeleganyu() {
        return "Chipeleganyu".equalsIgnoreCase(wizard.groupType);
    }

    private String validateCommonWizardDetails() {
        if (wizard.groupType.isBlank()) {
            return "Select a Savings Group type.";
        }
        if (wizard.groupName.isBlank()) {
            return "Group Name is required.";
        }
        if (wizard.startDate == null) {
            return "Start Date is required.";
        }
        if (wizard.endDate == null) {
            return "End Date is required.";
        }
        if (wizard.endDate.isBefore(wizard.startDate)) {
            return "End Date must not be before Start Date.";
        }
        return "";
    }

    private String validateRules() {
        if (wizard.frequency == null || wizard.frequency.isBlank()) {
            return "Contribution Frequency is required.";
        }
        if (wizard.sourceAccountId == null || wizard.sourceAccountId <= 0) {
            return "Source Account is required.";
        }
        if (isWizardBankNkhonde()) {
            if (wizard.shareAmount <= 0) {
                return "Share Amount must be greater than zero.";
            }
            if (wizard.requiredSharesPerPeriod <= 0) {
                return "Required Shares Per Month must be greater than zero.";
            }
            if (wizard.loanInterestRate < 0) {
                return "Loan interest rate cannot be negative.";
            }
        } else {
            if (wizard.monthlyContributionAmount <= 0) {
                return "Monthly Contribution Amount must be greater than zero.";
            }
            if (wizard.totalContributions <= 0) {
                return "Total Number of Contributions must be greater than zero.";
            }
        }
        return "";
    }

    private String wizardDuration() {
        if (wizard.startDate == null || wizard.endDate == null) {
            return "Not set";
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(
                YearMonth.from(wizard.startDate).atDay(1),
                YearMonth.from(wizard.endDate).atDay(1)) + 1;
        return Math.max(1, months) + " month(s)";
    }

    private String wizardRuleSummary() {
        if (isWizardBankNkhonde()) {
            return money(wizard.shareAmount) + " x " + wizard.requiredSharesPerPeriod
                    + " share(s) per period = " + money(wizard.shareAmount * wizard.requiredSharesPerPeriod);
        }
        return money(wizard.monthlyContributionAmount) + " per period for " + wizard.totalContributions + " contribution(s)";
    }

    private int expectedPeriods(SavingsGroupProfileRecord profile) {
        LocalDate start = parseDate(profile.actualStartDate());
        LocalDate end = parseDate(profile.expectedCycleEndDate());
        if (start == null || end == null) {
            return 0;
        }
        if ("Weekly".equalsIgnoreCase(profile.contributionFrequency())) {
            return Math.max(1, (int) (java.time.temporal.ChronoUnit.WEEKS.between(start, end) + 1));
        }
        return Math.max(1, (int) (java.time.temporal.ChronoUnit.MONTHS.between(YearMonth.from(start).atDay(1), YearMonth.from(end).atDay(1)) + 1));
    }

    private double estimatedBankShareOut(SavingsGroupProfileRecord profile, double outstandingLoans) {
        if (profile.expectedPayoutAmount() > 0) {
            return Math.max(0, profile.expectedPayoutAmount() - outstandingLoans);
        }
        return Math.max(0, profile.currentContributionBalance() - outstandingLoans);
    }

    private double minorToMajor(long amountMinor, String currency) {
        try {
            return Money.ofMinor(amountMinor, currency).toMajor().doubleValue();
        } catch (RuntimeException exception) {
            return BigDecimal.valueOf(amountMinor, 2).doubleValue();
        }
    }

    private String money(double amount) {
        return money(amount, baseCurrencyCode());
    }

    private String money(double amount, String currency) {
        return currencyLabel(currency) + " " + numberFormat.format(amount);
    }

    private String moneyMinor(long amountMinor, String currency) {
        return money(minorToMajor(amountMinor, currency), currency);
    }

    private String number(double value) {
        return numberFormat.format(value);
    }

    private String amountText(double amount) {
        return amount <= 0 ? "" : BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString();
    }

    private String displayType(String type) {
        if (isBankType(type)) {
            return "Bank Nkhonde";
        }
        String clean = safe(type).trim();
        return clean.isBlank() ? "-" : clean.replace('_', ' ');
    }

    private String displayStatus(String status) {
        String clean = safe(status).replace('_', ' ').trim().toLowerCase(Locale.ENGLISH);
        return clean.isBlank() ? "-" : Character.toUpperCase(clean.charAt(0)) + clean.substring(1);
    }

    private String displayClassification(String value) {
        return switch (safe(value)) {
            case "CONTRIBUTION" -> "Contribution";
            case "ORIGINAL_SAVINGS_RETURN" -> "Original Savings Return";
            case "PROFIT" -> "Profit";
            case "BONUS" -> "Bonus";
            default -> safe(value).replace('_', ' ');
        };
    }

    private double parseAmount(String value, String label) {
        double amount = parseOptionalAmount(value, label);
        if (amount <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return amount;
    }

    private double parseOptionalAmount(String value, String label) {
        String clean = clean(value).replace(",", "");
        if (clean.isBlank()) {
            return 0;
        }
        try {
            double amount = Double.parseDouble(clean);
            if (amount < 0) {
                throw new IllegalArgumentException(label + " cannot be negative.");
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid amount.");
        }
    }

    private int parseInt(String value, String label) {
        try {
            int parsed = Integer.parseInt(clean(value));
            if (parsed <= 0) {
                throw new IllegalArgumentException(label + " must be greater than zero.");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private YearMonth requireYearMonth(String value) {
        try {
            return YearMonth.parse(clean(value));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("Contribution Period must use yyyy-MM.");
        }
    }

    private <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private LocalDate parseDate(String value) {
        return parseDate(value, null);
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.substring(0, Math.min(10, value.length())));
        } catch (DateTimeParseException exception) {
            return fallback;
        }
    }

    private String safeDate(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private String safeDate(String value) {
        return safe(value);
    }

    private String accountName(Integer accountId) {
        if (accountId == null) {
            return "";
        }
        return activeAccounts.stream()
                .filter(account -> account.getId() == accountId)
                .map(Account::getAccountName)
                .findFirst()
                .orElse("");
    }

    private String baseCurrencyCode() {
        String code = database.getBaseCurrencyCode();
        return code == null || code.isBlank() ? Money.MWK : currencyLabel(code);
    }

    private String currentCurrencyCode() {
        return baseCurrencyCode();
    }

    private String requireCurrencyCode() {
        return currentCurrencyCode();
    }

    private String currencyLabel(String value) {
        String clean = safe(value).trim();
        if (clean.length() >= 3) {
            String firstThree = clean.substring(0, 3).toUpperCase(Locale.ENGLISH);
            if (firstThree.matches("[A-Z]{3}")) {
                return firstThree;
            }
        }
        return clean.isBlank() ? Money.MWK : clean.toUpperCase(Locale.ENGLISH);
    }

    private boolean matchesGroup(String value, String filter) {
        return filter == null || "All Groups".equals(filter) || safe(value).equals(filter);
    }

    private boolean matchesOption(String value, String filter, String allLabel) {
        return filter == null || allLabel.equals(filter) || safe(value).equalsIgnoreCase(filter);
    }

    private String optionalSuffix(String prefix, Object value) {
        if (value == null) {
            return "";
        }
        String clean = safe(String.valueOf(value));
        return clean.isBlank() ? "" : prefix + clean;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showLoanDetails(BankNkhondeLoanRecord loan) {
        showMessage("Bank Nkhonde Loan",
                "Reference: " + safe(loan.referenceNumber()) + "\n"
                        + "Principal: " + money(loan.principalAmount(), loan.currency()) + "\n"
                        + "Interest: " + money(loan.interestAmount(), loan.currency()) + "\n"
                        + "Balance: " + money(loan.balance(), loan.currency()) + "\n"
                        + "Status: " + displayStatus(loan.status()) + "\n"
                        + "Notes: " + safe(loan.notes()));
    }

    private void showLedgerDetails(LedgerRow row) {
        showMessage("Savings Ledger Details",
                "Reference: " + row.reference() + "\n"
                        + "Savings Group: " + row.group() + "\n"
                        + "Financial Account: " + row.account() + "\n"
                        + "Transaction Type: " + row.transactionType() + "\n"
                        + "Date: " + safeDate(row.date()) + "\n"
                        + "Debit: " + money(row.debit(), row.currency()) + "\n"
                        + "Credit: " + money(row.credit(), row.currency()) + "\n"
                        + "Status: " + row.status() + "\n"
                        + "Details: " + row.details());
    }

    private void showMessage(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("PFMIS");
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showProblem(String title, Throwable exception) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("PFMIS");
        alert.setHeaderText(title);
        alert.setContentText(UiAlerts.rootMessage(exception));
        alert.showAndWait();
    }

    private record ContributionRow(SavingsGroupProfileRecord profile, String group, String type, String period,
                                   double expected, double paid, String sourceAccount, String dueDate,
                                   String status, String currency) {
    }

    private record PayoutRow(SavingsGroupProfileRecord profile, String group, String type, double expected,
                             double actual, String expectedDate, String receivedDate, String destinationAccount,
                             String status, String currency) {
    }

    private record LedgerRow(LocalDate date, String reference, String group, String groupType,
                             String transactionType, String description, double debit, double credit,
                             Double positionBalance, String account, String status, String currency, String details) {
    }

    private static final class AddWizardState {
        private int step = 1;
        private String groupType = "";
        private String groupName = "";
        private LocalDate joiningDate = LocalDate.now();
        private LocalDate startDate = LocalDate.now();
        private LocalDate endDate;
        private String notes = "";
        private String status = "Active";
        private String frequency = "Monthly";
        private String contributionDay = "";
        private Integer sourceAccountId;
        private double shareAmount;
        private int requiredSharesPerPeriod = 1;
        private double loanInterestRate;
        private double monthlyContributionAmount;
        private int totalContributions = 12;
        private double expectedPayoutAmount;
        private boolean automaticContributionEnabled;

        private void reset() {
            step = 1;
            groupType = "";
            groupName = "";
            joiningDate = LocalDate.now();
            startDate = LocalDate.now();
            endDate = null;
            notes = "";
            status = "Active";
            frequency = "Monthly";
            contributionDay = "";
            sourceAccountId = null;
            shareAmount = 0;
            requiredSharesPerPeriod = 1;
            loanInterestRate = 0;
            monthlyContributionAmount = 0;
            totalContributions = 12;
            expectedPayoutAmount = 0;
            automaticContributionEnabled = false;
        }
    }
}
