package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.TransferPostingResult;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class GoalContributionController {
    private static final String METHOD_TRANSFER = "Transfer to dedicated goal account";
    private static final String METHOD_ALLOCATION = "Allocate existing savings";
    private static final String METHOD_EXTERNAL = "External contribution received";

    @FXML private ComboBox<Goal> goalBox;
    @FXML private DatePicker contributionDatePicker;
    @FXML private TextField amountField;
    @FXML private TextField currencyField;
    @FXML private ComboBox<String> methodBox;
    @FXML private ComboBox<Account> sourceAccountBox;
    @FXML private Label sourceBalanceLabel;
    @FXML private ComboBox<Account> destinationAccountBox;
    @FXML private Label destinationBalanceLabel;
    @FXML private TextField referenceField;
    @FXML private TextArea descriptionArea;
    @FXML private Label methodNoteLabel;
    @FXML private TextArea resultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        contributionDatePicker.setValue(LocalDate.now());
        methodBox.setItems(FXCollections.observableArrayList(METHOD_TRANSFER, METHOD_ALLOCATION, METHOD_EXTERNAL));
        methodBox.getSelectionModel().select(METHOD_ALLOCATION);
        methodBox.valueProperty().addListener((observable, oldValue, newValue) -> updateMethodState());
        goalBox.valueProperty().addListener((observable, oldValue, newValue) -> updateGoalState());
        sourceAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> updateAccountLabels());
        destinationAccountBox.valueProperty().addListener((observable, oldValue, newValue) -> updateAccountLabels());
        resultArea.setText("Select a goal and contribution method. Contributions update goal progress through the contribution ledger.");
        refresh();
        applyNavigationRequest();
    }

    @FXML
    private void recordContribution() {
        try {
            Goal goal = requireGoal();
            double amount = parsePositiveAmount(amountField.getText(), "Enter a contribution amount greater than zero.");
            LocalDate date = contributionDatePicker.getValue();
            if (date == null) {
                throw new IllegalArgumentException("Select the contribution date.");
            }
            String method = methodBox.getValue();
            String reference = textValue(referenceField);
            String description = contributionDescription(goal, method);
            int contributionId;
            if (METHOD_TRANSFER.equals(method)) {
                contributionId = recordTransferContribution(goal, amount, date, reference, description);
            } else if (METHOD_EXTERNAL.equals(method)) {
                contributionId = recordExternalContribution(goal, amount, date, reference, description);
            } else {
                contributionId = recordAllocationContribution(goal, amount, date, reference, description);
            }
            Goal updatedGoal = goalById(goal.getId());
            double remaining = updatedGoal == null ? Math.max(0, goal.getTargetAmount() - amount) : updatedGoal.getRemainingAmount();
            double allocated = updatedGoal == null ? amount : updatedGoal.getCurrentAmount();
            double progress = updatedGoal == null || updatedGoal.getTargetAmount() <= 0 ? 0 : allocated / updatedGoal.getTargetAmount() * 100;
            resultArea.setText("""
                    Contribution recorded successfully.

                    Contribution: #%d
                    Goal: %s
                    Contribution: %s
                    Total allocated: %s
                    Remaining amount: %s
                    Progress: %.1f%%
                    """.formatted(
                    contributionId,
                    goal.getGoalName(),
                    MoneyUtil.mwk(amount),
                    MoneyUtil.mwk(allocated),
                    MoneyUtil.mwk(remaining),
                    progress
            ));
            clearEntryFields();
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (IllegalArgumentException exception) {
            UiAlerts.info(exception.getMessage());
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to record goal contribution", exception);
        }
    }

    @FXML
    private void clearForm() {
        goalBox.setValue(null);
        clearEntryFields();
        resultArea.setText("Form cleared.");
    }

    private int recordTransferContribution(Goal goal, double amount, LocalDate date, String reference, String description) {
        Account source = sourceAccountBox.getValue();
        Account destination = destinationAccountBox.getValue();
        if (source == null || destination == null) {
            throw new IllegalArgumentException("Select the source and destination accounts for the goal transfer.");
        }
        if (source.getId() == destination.getId()) {
            throw new IllegalArgumentException("Choose two different accounts for a goal transfer.");
        }
        TransferPostingResult result = database.recordTransferWithFee(
                source.getId(),
                destination.getId(),
                amount,
                amount,
                0,
                null,
                date,
                description,
                "Goal contribution",
                reference
        );
        return database.recordGoalContribution(
                goal.getId(),
                date,
                amount,
                goalCurrency(goal),
                "TRANSFER",
                source.getId(),
                destination.getId(),
                result.incomingTransactionId(),
                result.transferReference(),
                "ACTIVE",
                description
        );
    }

    private int recordAllocationContribution(Goal goal, double amount, LocalDate date, String reference, String description) {
        Account source = sourceAccountBox.getValue();
        if (source == null) {
            throw new IllegalArgumentException("Select the account containing the savings being allocated.");
        }
        double alreadyAllocated = database.activeGoalAllocationForAccount(source.getId(), goal.getId());
        if (alreadyAllocated + amount > source.getCurrentBalance() + 0.005) {
            throw new IllegalArgumentException("This account does not have enough unallocated savings for this goal allocation.");
        }
        return database.recordGoalContribution(
                goal.getId(),
                date,
                amount,
                goalCurrency(goal),
                "ALLOCATION",
                source.getId(),
                null,
                null,
                reference,
                "ACTIVE",
                description
        );
    }

    private int recordExternalContribution(Goal goal, double amount, LocalDate date, String reference, String description) {
        Account destination = destinationAccountBox.getValue();
        if (destination == null) {
            throw new IllegalArgumentException("Select the account that received the external contribution.");
        }
        int transactionId = database.recordIncomeTransaction(
                destination.getId(),
                database.findOrCreateCategory("Goal Contribution", "INCOME").getId(),
                null,
                null,
                null,
                amount,
                goalCurrency(goal),
                date,
                description,
                "Goal contribution",
                reference
        );
        return database.recordGoalContribution(
                goal.getId(),
                date,
                amount,
                goalCurrency(goal),
                "EXTERNAL",
                null,
                destination.getId(),
                transactionId,
                reference,
                "ACTIVE",
                description
        );
    }

    private void refresh() {
        Goal selectedGoal = goalBox.getValue();
        List<Goal> goals = database.listGoals().stream()
                .filter(this::canReceiveContribution)
                .toList();
        goalBox.setItems(FXCollections.observableArrayList(goals));
        if (selectedGoal != null) {
            goals.stream()
                    .filter(goal -> goal.getId() == selectedGoal.getId())
                    .findFirst()
                    .ifPresent(goalBox::setValue);
        }
        List<Account> accounts = database.listAccounts().stream()
                .filter(account -> !"INACTIVE".equalsIgnoreCase(account.getStatus()))
                .toList();
        sourceAccountBox.setItems(FXCollections.observableArrayList(accounts));
        destinationAccountBox.setItems(FXCollections.observableArrayList(accounts));
        updateGoalState();
        updateMethodState();
        updateAccountLabels();
    }

    private void applyNavigationRequest() {
        Integer requestedGoalId = NavigationBus.consumeRequestedGoalId();
        if (requestedGoalId == null) {
            return;
        }
        goalBox.getItems().stream()
                .filter(goal -> goal.getId() == requestedGoalId)
                .findFirst()
                .ifPresent(goalBox::setValue);
    }

    private void updateGoalState() {
        Goal goal = goalBox.getValue();
        currencyField.setText(goal == null ? "MWK" : goalCurrency(goal));
        if (goal != null && goal.getFundingAccountId() != null) {
            sourceAccountBox.getItems().stream()
                    .filter(account -> account.getId() == goal.getFundingAccountId())
                    .findFirst()
                    .ifPresent(sourceAccountBox::setValue);
        }
    }

    private void updateMethodState() {
        String method = methodBox.getValue();
        boolean allocation = METHOD_ALLOCATION.equals(method);
        boolean external = METHOD_EXTERNAL.equals(method);
        sourceAccountBox.setDisable(external);
        destinationAccountBox.setDisable(allocation);
        methodNoteLabel.setText(switch (method == null ? "" : method) {
            case METHOD_TRANSFER -> "This creates an internal transfer and increases the goal allocation without creating expense.";
            case METHOD_EXTERNAL -> "This records income received and links it to the goal contribution ledger.";
            default -> "This reserves existing savings for one goal and checks that the same account balance is not double-allocated.";
        });
    }

    private void updateAccountLabels() {
        Account source = sourceAccountBox.getValue();
        Account destination = destinationAccountBox.getValue();
        sourceBalanceLabel.setText(source == null ? "Balance: -" : "Balance: " + money(source));
        destinationBalanceLabel.setText(destination == null ? "Balance: -" : "Balance: " + money(destination));
    }

    private Goal requireGoal() {
        Goal goal = goalBox.getValue();
        if (goal == null) {
            throw new IllegalArgumentException("Select a goal.");
        }
        if (!canReceiveContribution(goal)) {
            throw new IllegalArgumentException("Select an active, at-risk, overdue or paused goal.");
        }
        return goal;
    }

    private boolean canReceiveContribution(Goal goal) {
        String status = safe(goal.getStatus()).toUpperCase(Locale.ENGLISH);
        return !List.of("DRAFT", "ACHIEVED", "CONVERTED_TO_PROJECT", "CANCELLED", "ARCHIVED").contains(status);
    }

    private Goal goalById(int goalId) {
        return database.listGoals().stream()
                .filter(goal -> goal.getId() == goalId)
                .findFirst()
                .orElse(null);
    }

    private String contributionDescription(Goal goal, String method) {
        String typedDescription = textValue(descriptionArea);
        if (!typedDescription.isBlank()) {
            return typedDescription;
        }
        return method + " for goal #" + goal.getId() + " - " + goal.getGoalName();
    }

    private void clearEntryFields() {
        amountField.clear();
        referenceField.clear();
        descriptionArea.clear();
        contributionDatePicker.setValue(LocalDate.now());
    }

    private double parsePositiveAmount(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        try {
            double amount = Double.parseDouble(value.replace(",", "").trim());
            if (amount <= 0) {
                throw new IllegalArgumentException(message);
            }
            return amount;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid contribution amount.");
        }
    }

    private String goalCurrency(Goal goal) {
        return safe(goal.getCurrency()).isBlank() ? "MWK" : goal.getCurrency();
    }

    private String money(Account account) {
        return (safe(account.getCurrency()).isBlank() ? "MWK" : account.getCurrency()) + " " + MoneyUtil.mwk(account.getCurrentBalance()).replace("MWK ", "");
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String textValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
