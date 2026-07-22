package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Person;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

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
            UiAlerts.info("Person ledger is not implemented yet for " + selected.getFullName() + ".");
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
            UiAlerts.info("Statement printing is not implemented yet.");
        }
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
