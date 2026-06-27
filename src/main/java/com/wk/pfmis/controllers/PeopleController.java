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
        Person selected = selectedPerson("give money");
        if (selected != null) {
            UiAlerts.info("Use Transactions with purpose MONEY_LENT or SUPPORT_GIVEN for " + selected.getFullName() + ".");
        }
    }

    @FXML
    private void receiveMoney() {
        Person selected = selectedPerson("receive money");
        if (selected != null) {
            UiAlerts.info("Use Transactions with purpose LENT_REPAID for " + selected.getFullName() + ".");
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
}
