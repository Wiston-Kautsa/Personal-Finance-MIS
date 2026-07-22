package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.CurrencyRecord;
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

public class CurrenciesController {
    @FXML private TextField currencyNameField;
    @FXML private TextField currencyCodeField;
    @FXML private TextField symbolField;
    @FXML private TextField rateToBaseField;
    @FXML private ComboBox<String> statusBox;
    @FXML private Label statusLabel;
    @FXML private TableView<CurrencyRecord> currenciesTable;
    @FXML private TableColumn<CurrencyRecord, String> currencyNameColumn;
    @FXML private TableColumn<CurrencyRecord, String> currencyCodeColumn;
    @FXML private TableColumn<CurrencyRecord, String> symbolColumn;
    @FXML private TableColumn<CurrencyRecord, Double> rateToBaseColumn;
    @FXML private TableColumn<CurrencyRecord, String> currencyStatusColumn;
    @FXML private TableColumn<CurrencyRecord, String> updatedAtColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        currencyNameColumn.setCellValueFactory(new PropertyValueFactory<>("currencyName"));
        currencyCodeColumn.setCellValueFactory(new PropertyValueFactory<>("currencyCode"));
        symbolColumn.setCellValueFactory(new PropertyValueFactory<>("symbol"));
        rateToBaseColumn.setCellValueFactory(new PropertyValueFactory<>("rateToBase"));
        currencyStatusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        updatedAtColumn.setCellValueFactory(new PropertyValueFactory<>("updatedAt"));
        statusBox.setItems(FXCollections.observableArrayList("Active", "Base Currency", "Inactive"));
        statusBox.getSelectionModel().select("Active");
        currenciesTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                populateForm(selected);
            }
        });
        configureContextMenu();
        refresh();
    }

    @FXML
    private void saveCurrency() {
        try {
            saveCurrencyFromForm(statusValue(), "Currency saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save currency", exception);
        }
    }

    @FXML
    private void setSelectedAsBase() {
        try {
            CurrencyRecord selected = currenciesTable.getSelectionModel().getSelectedItem();
            String code = currentCode(selected);
            if (code.isBlank()) {
                UiAlerts.info("Select a currency or enter a currency code first.");
                return;
            }
            database.saveCurrency(
                    currentName(selected),
                    code,
                    currentSymbol(selected),
                    1,
                    "BASE"
            );
            refresh();
            DataRefreshBus.notifyDataChanged();
            statusLabel.setText(code.toUpperCase() + " is now the base currency.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to set base currency", exception);
        }
    }

    @FXML
    private void updateRates() {
        try {
            saveCurrencyFromForm(statusValue(), "Manual currency rate updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update currency rate", exception);
        }
    }

    @FXML
    private void clearForm() {
        currenciesTable.getSelectionModel().clearSelection();
        currencyNameField.setText("");
        currencyCodeField.setText("");
        symbolField.setText("");
        rateToBaseField.setText("1.00");
        statusBox.getSelectionModel().select("Active");
        statusLabel.setText("Ready.");
    }

    private void refresh() {
        currenciesTable.setItems(FXCollections.observableArrayList(database.listCurrencies()));
    }

    private void saveCurrencyFromForm(String status, String message) {
        CurrencyRecord selected = currenciesTable.getSelectionModel().getSelectedItem();
        database.saveCurrency(
                currentName(selected),
                currentCode(selected),
                currentSymbol(selected),
                parseRate(),
                status
        );
        refresh();
        DataRefreshBus.notifyDataChanged();
        statusLabel.setText(message);
    }

    private void populateForm(CurrencyRecord selected) {
        currencyNameField.setText(selected.getCurrencyName());
        currencyCodeField.setText(selected.getCurrencyCode());
        symbolField.setText(selected.getSymbol());
        rateToBaseField.setText(String.valueOf(selected.getRateToBase()));
        statusBox.getSelectionModel().select(switch (selected.getStatus()) {
            case "BASE" -> "Base Currency";
            case "INACTIVE" -> "Inactive";
            default -> "Active";
        });
        statusLabel.setText("Editing " + selected.getCurrencyCode() + ".");
    }

    private double parseRate() {
        try {
            return Double.parseDouble(text(rateToBaseField));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Rate to base must be a number.", exception);
        }
    }

    private String currentName(CurrencyRecord selected) {
        String value = text(currencyNameField);
        if (!value.isBlank()) {
            return value;
        }
        return selected == null ? "" : selected.getCurrencyName();
    }

    private String currentCode(CurrencyRecord selected) {
        String value = text(currencyCodeField);
        if (!value.isBlank()) {
            return value;
        }
        return selected == null ? "" : selected.getCurrencyCode();
    }

    private String currentSymbol(CurrencyRecord selected) {
        String value = text(symbolField);
        if (!value.isBlank()) {
            return value;
        }
        return selected == null ? "" : selected.getSymbol();
    }

    private String statusValue() {
        return switch (statusBox.getValue()) {
            case "Base Currency" -> "BASE";
            case "Inactive" -> "INACTIVE";
            default -> "ACTIVE";
        };
    }

    private String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(currenciesTable, this::currencyMenuItems);
    }

    private List<javafx.scene.control.MenuItem> currencyMenuItems(CurrencyRecord currency) {
        List<javafx.scene.control.MenuItem> items = new ArrayList<>();
        items.add(TableActions.menuItem("Edit Currency", () -> populateForm(currency)));
        if (!currency.isBaseCurrency()) {
            items.add(TableActions.menuItem("Set As Base Currency", () -> updateCurrencyStatus(currency, "BASE")));
            if ("INACTIVE".equals(currency.getStatus())) {
                items.add(TableActions.menuItem("Mark Active", () -> updateCurrencyStatus(currency, "ACTIVE")));
            } else {
                items.add(TableActions.menuItem("Mark Inactive", () -> updateCurrencyStatus(currency, "INACTIVE")));
            }
        }
        items.add(TableActions.separator());
        items.add(TableActions.copyRowItem(currenciesTable, currency));
        items.add(TableActions.exportTableItem(currenciesTable, "Currencies"));
        items.add(TableActions.printTableItem(currenciesTable, "Currencies"));
        items.add(TableActions.refreshItem(this::refresh));
        return items;
    }

    private void updateCurrencyStatus(CurrencyRecord currency, String status) {
        try {
            database.saveCurrency(
                    currency.getCurrencyName(),
                    currency.getCurrencyCode(),
                    currency.getSymbol(),
                    currency.getRateToBase(),
                    status
            );
            refresh();
            DataRefreshBus.notifyDataChanged();
            statusLabel.setText(currency.getCurrencyCode() + " updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to update currency", exception);
        }
    }
}
