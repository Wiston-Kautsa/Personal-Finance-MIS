package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.utils.ExportPathService;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class PeopleController {
    @FXML private TextField fullNameField;
    @FXML private TextField phoneField;
    @FXML private TextField relationshipField;
    @FXML private TextArea notesArea;
    @FXML private TableView<Person> peopleTable;
    @FXML private TableColumn<Person, String> nameColumn;
    @FXML private TableColumn<Person, String> phoneColumn;
    @FXML private TableColumn<Person, String> relationshipColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("fullName"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phoneNumber"));
        relationshipColumn.setCellValueFactory(new PropertyValueFactory<>("relationship"));
        configureContextMenu();
        refresh();
    }

    @FXML
    private void addPerson() {
        try {
            String fullName = fullNameField.getText().trim();
            if (fullName.isEmpty()) {
                UiAlerts.info("Enter a full name.");
                return;
            }
            database.addPerson(fullName, phoneField.getText().trim(), relationshipField.getText().trim(), notesArea.getText().trim());
            fullNameField.clear();
            phoneField.clear();
            relationshipField.clear();
            notesArea.clear();
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to add person", exception);
        }
    }

    @FXML
    private void refresh() {
        peopleTable.setItems(FXCollections.observableArrayList(database.listPeople()));
    }

    @FXML
    private void viewLedger() {
        Person selected = selectedPerson("view ledger");
        if (selected != null) {
            try {
                showTextDialog("Person Ledger - " + selected.getFullName(), personLedger(selected));
                database.recordSystemLog("People", "View person ledger", "INFO", selected.getFullName());
            } catch (RuntimeException exception) {
                UiAlerts.error("Failed to load person ledger", exception);
            }
        }
    }

    @FXML
    private void giveMoney() {
        Person selected = selectedOrEnteredPerson("lend money");
        if (selected != null) {
            NavigationBus.requestTransaction("EXPENSE", "MONEY_LENT", selected.getFullName());
            NavigationBus.showTransactionEntry("Lend Money");
        }
    }

    @FXML
    private void receiveMoney() {
        Person selected = selectedOrEnteredPerson("receive money");
        if (selected != null) {
            NavigationBus.requestTransaction("INCOME", "LENT_REPAID", selected.getFullName());
            NavigationBus.showTransactionEntry("Receive Money");
        }
    }

    @FXML
    private void printStatement() {
        Person selected = selectedPerson("print a statement");
        if (selected != null) {
            try {
                String statement = personLedger(selected);
                Path file = exportPersonStatement(selected, statement);
                UiAlerts.info("Statement exported:\n" + file);
            } catch (RuntimeException exception) {
                UiAlerts.error("Failed to export person statement", exception);
            }
        }
    }

    private String personLedger(Person person) {
        List<FinanceTransaction> transactions = database.listLoanTransactionsForPerson(person.getId());
        StringBuilder builder = new StringBuilder();
        builder.append("Person loan ledger").append(System.lineSeparator())
                .append("Generated: ").append(LocalDateTime.now()).append(System.lineSeparator())
                .append("Person: ").append(person.getFullName()).append(System.lineSeparator())
                .append("Phone: ").append(safe(person.getPhoneNumber())).append(System.lineSeparator())
                .append("Relationship: ").append(safe(person.getRelationship())).append(System.lineSeparator())
                .append(System.lineSeparator());

        if (transactions.isEmpty()) {
            builder.append("No linked lending, borrowing or repayment transactions were found for this person.");
            return builder.toString();
        }

        double balance = 0;
        double lent = 0;
        double lentRepaid = 0;
        double borrowed = 0;
        double borrowedRepaid = 0;
        builder.append("Date       | Purpose          | Amount        | Net Change    | Balance       | Account | Reference | Description")
                .append(System.lineSeparator());
        builder.append("-----------|------------------|---------------|---------------|---------------|---------|-----------|------------")
                .append(System.lineSeparator());
        for (FinanceTransaction transaction : transactions) {
            double signedAmount = signedLoanAmount(transaction);
            boolean cancelled = "CANCELLED".equalsIgnoreCase(safe(transaction.getTransactionStatus()));
            if (!cancelled) {
                balance += signedAmount;
                switch (safe(transaction.getTransactionPurpose()).toUpperCase(Locale.ENGLISH)) {
                    case "MONEY_LENT", "SUPPORT_GIVEN" -> lent += transaction.getAmount();
                    case "LENT_REPAID" -> lentRepaid += transaction.getAmount();
                    case "MONEY_BORROWED" -> borrowed += transaction.getAmount();
                    case "BORROWED_REPAID" -> borrowedRepaid += transaction.getAmount();
                    default -> {
                    }
                }
            }
            builder.append(safe(transaction.getTransactionDate())).append(" | ")
                    .append(pad(purposeLabel(transaction.getTransactionPurpose()), 16)).append(" | ")
                    .append(pad(formatMoney(transaction.getAmount()), 13)).append(" | ")
                    .append(pad(cancelled ? "cancelled" : formatMoney(signedAmount), 13)).append(" | ")
                    .append(pad(formatMoney(balance), 13)).append(" | ")
                    .append(safe(transaction.getAccountName())).append(" | ")
                    .append(safe(transaction.getReferenceNumber())).append(" | ")
                    .append(safe(transaction.getDescription()))
                    .append(System.lineSeparator());
        }

        builder.append(System.lineSeparator())
                .append("Totals").append(System.lineSeparator())
                .append("Money lent/support given: ").append(formatMoney(lent)).append(System.lineSeparator())
                .append("Lent repaid: ").append(formatMoney(lentRepaid)).append(System.lineSeparator())
                .append("Money borrowed: ").append(formatMoney(borrowed)).append(System.lineSeparator())
                .append("Borrowed repaid: ").append(formatMoney(borrowedRepaid)).append(System.lineSeparator())
                .append("Net position: ").append(formatMoney(balance)).append(System.lineSeparator())
                .append("Positive net position means the person owes you. Negative net position means you owe the person.");
        return builder.toString();
    }

    private Path exportPersonStatement(Person person, String statement) {
        try {
            Path file = ExportPathService.writeTextExport(
                    ExportPathService.defaultFileName("Person Statement " + slug(person.getFullName()), "txt"),
                    statement
            );
            database.recordSystemLog("People", "Export person statement", "INFO", person.getFullName() + ": " + file);
            return file;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write person statement", exception);
        }
    }

    private void showTextDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("PFMIS");
        alert.setHeaderText(title);
        TextArea textArea = new TextArea(content);
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setPrefRowCount(22);
        textArea.setPrefColumnCount(95);
        alert.getDialogPane().setContent(textArea);
        alert.showAndWait();
    }

    private double signedLoanAmount(FinanceTransaction transaction) {
        return switch (safe(transaction.getTransactionPurpose()).toUpperCase(Locale.ENGLISH)) {
            case "MONEY_LENT", "SUPPORT_GIVEN" -> transaction.getAmount();
            case "LENT_REPAID" -> -transaction.getAmount();
            case "MONEY_BORROWED" -> -transaction.getAmount();
            case "BORROWED_REPAID" -> transaction.getAmount();
            default -> 0;
        };
    }

    private String purposeLabel(String purpose) {
        return safe(purpose).replace('_', ' ');
    }

    private String formatMoney(double amount) {
        return String.format(Locale.US, "MWK %,.2f", amount);
    }

    private String pad(String value, int width) {
        String clean = safe(value);
        return clean.length() >= width ? clean : clean + " ".repeat(width - clean.length());
    }

    private String slug(String value) {
        String slug = safe(value).toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "person" : slug;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private Person selectedPerson(String action) {
        Person selected = peopleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a person to " + action + ".");
        }
        return selected;
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(peopleTable, this::personMenuItems);
    }

    private List<javafx.scene.control.MenuItem> personMenuItems(Person person) {
        return List.of(
                TableActions.menuItem("Use Contact In Form", () -> populateForm(person)),
                TableActions.menuItem("Lend Money", this::giveMoney),
                TableActions.menuItem("Receive Repayment", this::receiveMoney),
                TableActions.menuItem("View Ledger", this::viewLedger),
                TableActions.menuItem("Print Statement", this::printStatement),
                TableActions.separator(),
                TableActions.copyRowItem(peopleTable, person),
                TableActions.exportTableItem(peopleTable, "Loan Contacts"),
                TableActions.printTableItem(peopleTable, "Loan Contacts"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void populateForm(Person person) {
        if (person == null) {
            return;
        }
        fullNameField.setText(person.getFullName());
        phoneField.setText(person.getPhoneNumber() == null ? "" : person.getPhoneNumber());
        relationshipField.setText(person.getRelationship() == null ? "" : person.getRelationship());
        notesArea.setText(person.getNotes() == null ? "" : person.getNotes());
    }

    private Person selectedOrEnteredPerson(String action) {
        Person selected = peopleTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }

        String fullName = fullNameField.getText() == null ? "" : fullNameField.getText().trim();
        if (fullName.isEmpty()) {
            UiAlerts.info("Select a person or enter a name to " + action + ".");
            return null;
        }

        return database.listPeople().stream()
                .filter(person -> person.getFullName().equalsIgnoreCase(fullName))
                .findFirst()
                .orElseGet(() -> {
                    database.addPerson(
                            fullName,
                            phoneField.getText() == null ? "" : phoneField.getText().trim(),
                            relationshipField.getText() == null ? "" : relationshipField.getText().trim(),
                            notesArea.getText() == null ? "" : notesArea.getText().trim()
                    );
                    refresh();
                    DataRefreshBus.notifyDataChanged();
                    return database.listPeople().stream()
                            .filter(person -> person.getFullName().equalsIgnoreCase(fullName))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Failed to create person"));
                });
    }
}
