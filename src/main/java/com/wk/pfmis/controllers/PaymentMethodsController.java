package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.PaymentMethodRecord;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.ArrayList;
import java.util.List;

public class PaymentMethodsController {
    @FXML private TextField methodNameField;
    @FXML private ComboBox<String> methodTypeBox;
    @FXML private TextField providerField;
    @FXML private TextField defaultAccountField;
    @FXML private ComboBox<String> statusBox;
    @FXML private Label statusLabel;
    @FXML private TableView<PaymentMethodRecord> paymentMethodsTable;
    @FXML private TableColumn<PaymentMethodRecord, String> methodNameColumn;
    @FXML private TableColumn<PaymentMethodRecord, String> methodTypeColumn;
    @FXML private TableColumn<PaymentMethodRecord, String> providerColumn;
    @FXML private TableColumn<PaymentMethodRecord, String> defaultAccountColumn;
    @FXML private TableColumn<PaymentMethodRecord, String> methodStatusColumn;
    @FXML private TableColumn<PaymentMethodRecord, String> lastUsedColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        methodNameColumn.setCellValueFactory(new PropertyValueFactory<>("methodName"));
        methodTypeColumn.setCellValueFactory(new PropertyValueFactory<>("methodType"));
        providerColumn.setCellValueFactory(new PropertyValueFactory<>("provider"));
        defaultAccountColumn.setCellValueFactory(new PropertyValueFactory<>("defaultAccount"));
        methodStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        lastUsedColumn.setCellValueFactory(new PropertyValueFactory<>("lastUsed"));
        methodTypeBox.setItems(FXCollections.observableArrayList("Cash", "Bank", "Mobile Money", "Card", "Cheque", "Other"));
        statusBox.setItems(FXCollections.observableArrayList("Active", "Inactive"));
        methodTypeBox.getSelectionModel().select("Bank");
        statusBox.getSelectionModel().select("Active");
        paymentMethodsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populateForm(selected);
            }
        });
        configureContextMenu();
        refresh();
    }

    @FXML
    private void saveMethod() {
        try {
            database.savePaymentMethod(
                    text(methodNameField),
                    methodTypeValue(),
                    text(providerField),
                    text(defaultAccountField),
                    statusValue()
            );
            refresh();
            statusLabel.setText("Payment method saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save payment method", exception);
        }
    }

    @FXML
    private void clearForm() {
        paymentMethodsTable.getSelectionModel().clearSelection();
        methodNameField.setText("");
        providerField.setText("");
        defaultAccountField.setText("");
        methodTypeBox.getSelectionModel().select("Bank");
        statusBox.getSelectionModel().select("Active");
        statusLabel.setText("Ready.");
    }

    @FXML
    private void saveDefaults() {
        try {
            database.savePaymentMethod("Cash", "Cash", "", "", "ACTIVE");
            database.savePaymentMethod("Bank Transfer", "Bank", "", "", "ACTIVE");
            database.savePaymentMethod("Mobile Money", "Mobile Money", "", "", "ACTIVE");
            database.savePaymentMethod("Card", "Card", "", "", "ACTIVE");
            refresh();
            DataRefreshBus.notifyDataChanged();
            statusLabel.setText("Default payment methods saved and activated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save default payment methods", exception);
        }
    }

    private void refresh() {
        paymentMethodsTable.setItems(FXCollections.observableArrayList(database.listPaymentMethods()));
    }

    private void populateForm(PaymentMethodRecord selected) {
        methodNameField.setText(selected.getMethodName());
        methodTypeBox.setValue(selected.getMethodType());
        providerField.setText(selected.getProvider());
        defaultAccountField.setText(selected.getDefaultAccount());
        statusBox.getSelectionModel().select("INACTIVE".equals(selected.getStatus()) ? "Inactive" : "Active");
        statusLabel.setText("Editing " + selected.getMethodName() + ".");
    }

    private String methodTypeValue() {
        String value = methodTypeBox.getValue();
        return value == null || value.isBlank() ? "Other" : value.trim();
    }

    private String statusValue() {
        return "Inactive".equals(statusBox.getValue()) ? "INACTIVE" : "ACTIVE";
    }

    private String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(paymentMethodsTable, this::paymentMethodMenuItems);
    }

    private List<javafx.scene.control.MenuItem> paymentMethodMenuItems(PaymentMethodRecord method) {
        List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem("Edit Payment Method", () -> populateForm(method)));
        if ("INACTIVE".equals(method.getStatus())) {
            items.add(TableActions.menuItem("Mark Active", () -> updateMethodStatus(method, "ACTIVE")));
        } else {
            items.add(TableActions.menuItem("Mark Inactive", () -> updateMethodStatus(method, "INACTIVE")));
        }
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(paymentMethodsTable, method));
        items.add(TableActions.exportTableItem(paymentMethodsTable, "Payment Methods"));
        items.add(TableActions.printTableItem(paymentMethodsTable, "Payment Methods"));
        items.add(TableActions.refreshItem(this::refresh));
        return items;
    }

    private void updateMethodStatus(PaymentMethodRecord method, String status) {
        try {
            database.savePaymentMethod(
                    method.getMethodName(),
                    method.getMethodType(),
                    method.getProvider(),
                    method.getDefaultAccount(),
                    status
            );
            refresh();
            DataRefreshBus.notifyDataChanged();
            statusLabel.setText(method.getMethodName() + " updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update payment method", exception);
        }
    }
}
