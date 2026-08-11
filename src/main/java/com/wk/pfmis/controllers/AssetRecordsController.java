package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.AssetEvent;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AssetRecordsController {
    private static final String ALL_STATUSES = "All statuses";
    private static final String ALL_CATEGORIES = "All categories";

    @FXML private ComboBox<String> statusFilterBox;
    @FXML private ComboBox<String> categoryFilterBox;
    @FXML private TextField searchField;
    @FXML private TableView<Asset> assetsTable;
    @FXML private TableColumn<Asset, String> nameColumn;
    @FXML private TableColumn<Asset, String> categoryColumn;
    @FXML private TableColumn<Asset, String> valueColumn;
    @FXML private TableColumn<Asset, String> quantityColumn;
    @FXML private TableColumn<Asset, String> statusColumn;
    @FXML private TableColumn<Asset, String> locationColumn;
    @FXML private TableColumn<Asset, String> acquisitionColumn;
    @FXML private TableColumn<Asset, String> linkColumn;
    @FXML private Label openAssetTitleLabel;
    @FXML private TextArea overviewArea;
    @FXML private TextArea financeArea;
    @FXML private TextArea historyArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ObservableList<Asset> assets = FXCollections.observableArrayList();
    private Asset openedAsset;

    @FXML
    public void initialize() {
        configureTable();
        statusFilterBox.setItems(FXCollections.observableArrayList(ALL_STATUSES));
        categoryFilterBox.setItems(FXCollections.observableArrayList(ALL_CATEGORIES));
        statusFilterBox.setValue(ALL_STATUSES);
        categoryFilterBox.setValue(ALL_CATEGORIES);
        statusFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        categoryFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        assetsTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                showAsset(newValue);
            }
        });
        refresh();
    }

    @FXML
    private void refresh() {
        List<Asset> latest = database.listAssets();
        assets.setAll(latest);
        configureFilters(latest);
        applyFilters();
        if (!assetsTable.getItems().isEmpty()) {
            assetsTable.getSelectionModel().selectFirst();
        } else {
            openedAsset = null;
            openAssetTitleLabel.setText("No asset selected");
            overviewArea.setText("Register assets first. All acquisition paths will feed into this Asset Records register.");
            financeArea.clear();
            historyArea.clear();
        }
    }

    @FXML
    private void openAsset() {
        showAsset(selectedAsset());
    }

    @FXML
    private void editDetails() {
        Asset asset = selectedAsset();
        Dialog<ButtonType> dialog = dialog("Edit Asset Details");
        GridPane grid = dialogGrid();
        TextField nameField = new TextField(asset.getAssetName());
        TextField categoryField = new TextField(asset.getAssetCategory());
        TextField supplierField = new TextField(orEmpty(asset.getSupplier()));
        TextField referenceField = new TextField(orEmpty(asset.getReferenceNumber()));
        TextField serialField = new TextField(orEmpty(asset.getSerialNumber()));
        TextField locationField = new TextField(orEmpty(asset.getLocation()));
        TextField conditionField = new TextField(orEmpty(asset.getAssetCondition()));
        TextField supportField = new TextField(orEmpty(asset.getSupportingDocument()));
        TextArea notesArea = notesField();
        notesArea.setText(orEmpty(asset.getNotes()));
        addRow(grid, 0, "Asset name", nameField);
        addRow(grid, 1, "Category", categoryField);
        addRow(grid, 2, "Supplier or source", supplierField);
        addRow(grid, 3, "Reference number", referenceField);
        addRow(grid, 4, "Serial or registration", serialField);
        addRow(grid, 5, "Location", locationField);
        addRow(grid, 6, "Condition", conditionField);
        addRow(grid, 7, "Supporting document", supportField);
        addRow(grid, 8, "Notes", notesArea);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent()) {
            try {
                database.updateAssetDetails(
                        asset.getId(),
                        nameField.getText(),
                        categoryField.getText(),
                        supplierField.getText(),
                        referenceField.getText(),
                        serialField.getText(),
                        locationField.getText(),
                        conditionField.getText(),
                        supportField.getText(),
                        notesArea.getText()
                );
                refresh();
            } catch (RuntimeException exception) {
                UiAlerts.error("Could not update asset details", exception);
            }
        }
    }

    @FXML
    private void updateValue() {
        Asset asset = selectedAsset();
        Dialog<ButtonType> dialog = dialog("Update Asset Value");
        GridPane grid = dialogGrid();
        TextField valueField = new TextField(String.valueOf(asset.getCurrentValue()));
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField reasonField = new TextField("Updated valuation");
        TextArea notesArea = notesField();
        addRow(grid, 0, "New value", valueField);
        addRow(grid, 1, "Valuation date", datePicker);
        addRow(grid, 2, "Reason", reasonField);
        addRow(grid, 3, "Notes", notesArea);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent()) {
            try {
                database.updateAssetValue(
                        asset.getId(),
                        parseAmount(valueField, "New value"),
                        datePicker.getValue() == null ? LocalDate.now().toString() : datePicker.getValue().toString(),
                        reasonField.getText(),
                        notesArea.getText()
                );
                refresh();
            } catch (RuntimeException exception) {
                UiAlerts.error("Could not update asset value", exception);
            }
        }
    }

    @FXML
    private void recordMaintenance() {
        Asset asset = selectedAsset();
        Dialog<ButtonType> dialog = dialog("Record Maintenance");
        GridPane grid = dialogGrid();
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField costField = new TextField("0");
        TextField providerField = new TextField();
        TextField referenceField = new TextField();
        CheckBox addToValueBox = new CheckBox("Add this cost to asset value");
        TextArea notesArea = notesField();
        addRow(grid, 0, "Date", datePicker);
        addRow(grid, 1, "Cost", costField);
        addRow(grid, 2, "Provider", providerField);
        addRow(grid, 3, "Reference", referenceField);
        grid.add(addToValueBox, 1, 4);
        addRow(grid, 5, "Notes", notesArea);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent()) {
            try {
                database.recordAssetMaintenance(
                        asset.getId(),
                        datePicker.getValue() == null ? LocalDate.now().toString() : datePicker.getValue().toString(),
                        parseAmount(costField, "Maintenance cost"),
                        providerField.getText(),
                        referenceField.getText(),
                        notesArea.getText(),
                        addToValueBox.isSelected()
                );
                refresh();
            } catch (RuntimeException exception) {
                UiAlerts.error("Could not record maintenance", exception);
            }
        }
    }

    @FXML
    private void linkPurchaseTransaction() {
        Asset asset = selectedAsset();
        Dialog<ButtonType> dialog = dialog("Link Purchase Transaction");
        GridPane grid = dialogGrid();
        TextField transactionField = new TextField(asset.getPurchaseTransactionId() == null ? "" : String.valueOf(asset.getPurchaseTransactionId()));
        TextArea notesArea = notesField();
        addRow(grid, 0, "Transaction ID", transactionField);
        addRow(grid, 1, "Allocation notes", notesArea);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent()) {
            try {
                database.linkAssetPurchaseTransaction(asset.getId(), parseInt(transactionField, "Transaction ID"), notesArea.getText());
                refresh();
            } catch (RuntimeException exception) {
                UiAlerts.error("Could not link purchase transaction", exception);
            }
        }
    }

    @FXML
    private void sellAsset() {
        Asset asset = selectedAsset();
        Dialog<ButtonType> dialog = dialog("Sell Asset");
        GridPane grid = dialogGrid();
        ComboBox<String> saleTypeBox = new ComboBox<>(FXCollections.observableArrayList("Full Sale", "Partial Sale"));
        saleTypeBox.setValue("Full Sale");
        DatePicker saleDatePicker = new DatePicker(LocalDate.now());
        TextField buyerField = new TextField();
        TextField salePriceField = new TextField(String.valueOf(asset.getCurrentValue()));
        TextField sellingCostsField = new TextField("0");
        ComboBox<Account> accountBox = new ComboBox<>(FXCollections.observableArrayList(activeAccounts()));
        ComboBox<String> paymentMethodBox = new ComboBox<>(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        if (!paymentMethodBox.getItems().isEmpty()) {
            paymentMethodBox.setValue(paymentMethodBox.getItems().get(0));
        }
        TextField referenceField = new TextField();
        ComboBox<String> paymentOptionBox = new ComboBox<>(FXCollections.observableArrayList(
                "Full payment received",
                "Partial payment received",
                "Payment to be received later",
                "Asset exchanged for another asset"
        ));
        paymentOptionBox.setValue("Full payment received");
        TextField amountReceivedField = new TextField(String.valueOf(asset.getCurrentValue()));
        TextField dueDateField = new TextField();
        TextField quantitySoldField = new TextField(String.valueOf(asset.getQuantity()));
        TextField valueRemovedField = new TextField(String.valueOf(asset.getCurrentValue()));
        TextField reasonField = new TextField("Asset sold");
        TextField supportField = new TextField();
        TextArea notesArea = notesField();
        addRow(grid, 0, "Sale type", saleTypeBox);
        addRow(grid, 1, "Sale date", saleDatePicker);
        addRow(grid, 2, "Buyer", buyerField);
        addRow(grid, 3, "Sale price (" + asset.getCurrency() + ")", salePriceField);
        addRow(grid, 4, "Receiving account", accountBox);
        addRow(grid, 5, "Payment option", paymentOptionBox);
        addRow(grid, 6, "Amount received", amountReceivedField);
        addRow(grid, 7, "Due date", dueDateField);
        addRow(grid, 8, "Selling costs", sellingCostsField);
        addRow(grid, 9, "Quantity or portion sold", quantitySoldField);
        addRow(grid, 10, "Value removed", valueRemovedField);
        addRow(grid, 11, "Payment method", paymentMethodBox);
        addRow(grid, 12, "Reference number", referenceField);
        addRow(grid, 13, "Reason", reasonField);
        addRow(grid, 14, "Supporting document", supportField);
        addRow(grid, 15, "Notes", notesArea);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent()) {
            try {
                double salePrice = parseAmount(salePriceField, "Sale price");
                if (asset.getCurrentValue() > 0 && salePrice < asset.getCurrentValue() * 0.75
                        && !UiAlerts.confirm("Low sale price", "The sale price is significantly lower than the latest recorded value. Continue?")) {
                    return;
                }
                Account receivingAccount = accountBox.getValue();
                database.sellAsset(
                        asset.getId(),
                        "Partial Sale".equals(saleTypeBox.getValue()) ? "PARTIAL_SALE" : "FULL_SALE",
                        saleDatePicker.getValue() == null ? LocalDate.now().toString() : saleDatePicker.getValue().toString(),
                        buyerField.getText(),
                        salePrice,
                        asset.getCurrency(),
                        receivingAccount == null ? null : receivingAccount.getId(),
                        paymentMethodBox.getValue(),
                        referenceField.getText(),
                        parseAmount(sellingCostsField, "Selling costs"),
                        reasonField.getText(),
                        supportField.getText(),
                        notesArea.getText(),
                        paymentOptionBox.getValue(),
                        parseAmount(amountReceivedField, "Amount received"),
                        dueDateField.getText(),
                        parseAmount(quantitySoldField, "Quantity or portion sold"),
                        parseAmount(valueRemovedField, "Value removed")
                );
                refresh();
            } catch (RuntimeException exception) {
                UiAlerts.error("Could not sell asset", exception);
            }
        }
    }

    @FXML private void transferAsset() {
        statusAction("Transferred", "TRANSFER", "Recipient");
    }

    @FXML private void donateAsset() {
        statusAction("Donated", "DONATION", "Recipient");
    }

    @FXML private void writeOffAsset() {
        statusAction("Written Off", "WRITE_OFF", "Approved by");
    }

    @FXML private void markLost() {
        statusAction("Lost", "LOST", "Reported by");
    }

    @FXML private void disposeAsset() {
        statusAction("Disposed", "DISPOSAL", "Disposed by");
    }

    @FXML private void archiveAsset() {
        statusAction("Archived", "ARCHIVE", "Archived by");
    }

    private void statusAction(String status, String eventType, String counterpartyLabel) {
        Asset asset = selectedAsset();
        Dialog<ButtonType> dialog = dialog(status + " Asset");
        GridPane grid = dialogGrid();
        DatePicker datePicker = new DatePicker(LocalDate.now());
        TextField counterpartyField = new TextField();
        TextField reasonField = new TextField();
        TextField referenceField = new TextField();
        TextArea notesArea = notesField();
        addRow(grid, 0, "Date", datePicker);
        addRow(grid, 1, counterpartyLabel, counterpartyField);
        addRow(grid, 2, "Reason", reasonField);
        addRow(grid, 3, "Reference", referenceField);
        addRow(grid, 4, "Notes", notesArea);
        dialog.getDialogPane().setContent(grid);
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent()) {
            try {
                database.updateAssetStatusWithEvent(
                        asset.getId(),
                        status,
                        eventType,
                        datePicker.getValue() == null ? LocalDate.now().toString() : datePicker.getValue().toString(),
                        counterpartyField.getText(),
                        reasonField.getText(),
                        referenceField.getText(),
                        notesArea.getText()
                );
                refresh();
            } catch (RuntimeException exception) {
                UiAlerts.error("Could not update asset status", exception);
            }
        }
    }

    private void configureTable() {
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAssetName()));
        categoryColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAssetCategory()));
        valueColumn.setCellValueFactory(data -> new SimpleStringProperty(format(data.getValue().getCurrency(), data.getValue().getCurrentValue())));
        quantityColumn.setCellValueFactory(data -> new SimpleStringProperty(formatNumber(data.getValue().getQuantity())));
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(displayStatus(data.getValue().getStatus())));
        locationColumn.setCellValueFactory(data -> new SimpleStringProperty(orBlank(data.getValue().getLocation())));
        acquisitionColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAcquisitionMethod()));
        linkColumn.setCellValueFactory(data -> new SimpleStringProperty(linkSummary(data.getValue())));
    }

    private void configureFilters(List<Asset> latest) {
        Set<String> statuses = new LinkedHashSet<>();
        statuses.add(ALL_STATUSES);
        Set<String> categories = new LinkedHashSet<>();
        categories.add(ALL_CATEGORIES);
        for (Asset asset : latest) {
            statuses.add(displayStatus(asset.getStatus()));
            categories.add(asset.getAssetCategory());
        }
        String selectedStatus = statusFilterBox.getValue();
        String selectedCategory = categoryFilterBox.getValue();
        statusFilterBox.setItems(FXCollections.observableArrayList(statuses));
        categoryFilterBox.setItems(FXCollections.observableArrayList(categories));
        statusFilterBox.setValue(statuses.contains(selectedStatus) ? selectedStatus : ALL_STATUSES);
        categoryFilterBox.setValue(categories.contains(selectedCategory) ? selectedCategory : ALL_CATEGORIES);
    }

    private void applyFilters() {
        String status = statusFilterBox.getValue();
        String category = categoryFilterBox.getValue();
        String search = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase(Locale.ENGLISH);
        assetsTable.setItems(FXCollections.observableArrayList(
                assets.stream()
                        .filter(asset -> ALL_STATUSES.equals(status) || displayStatus(asset.getStatus()).equals(status))
                        .filter(asset -> ALL_CATEGORIES.equals(category) || asset.getAssetCategory().equals(category))
                        .filter(asset -> search.isBlank() || searchableText(asset).contains(search))
                        .collect(Collectors.toList())
        ));
    }

    private void showAsset(Asset asset) {
        openedAsset = asset;
        openAssetTitleLabel.setText(asset.getAssetName() + " - " + displayStatus(asset.getStatus()));
        List<AssetEvent> events = database.listAssetEvents(asset.getId());
        overviewArea.setText(overviewText(asset));
        financeArea.setText(financeText(asset, events));
        historyArea.setText(historyText(events));
    }

    private String overviewText(Asset asset) {
        return """
                Overview

                Asset: %s
                Category: %s
                Status: %s
                Location: %s
                Condition: %s
                Quantity or portion remaining: %s

                Value

                Purchase cost: %s
                Capitalized costs: %s
                Current book value: %s

                Acquisition

                Method: %s
                Date: %s
                Supplier or source: %s
                Serial or registration number: %s
                Supporting document: %s

                Notes

                %s
                """.formatted(
                asset.getAssetName(),
                asset.getAssetCategory(),
                displayStatus(asset.getStatus()),
                orBlank(asset.getLocation()),
                orBlank(asset.getAssetCondition()),
                formatNumber(asset.getQuantity()),
                format(asset.getCurrency(), asset.getPurchaseCost()),
                format(asset.getCurrency(), asset.getCapitalizedCosts()),
                format(asset.getCurrency(), asset.getCurrentValue()),
                asset.getAcquisitionMethod(),
                orBlank(asset.getPurchaseDate()),
                orBlank(asset.getSupplier()),
                orBlank(asset.getSerialNumber()),
                orBlank(asset.getSupportingDocument()),
                orBlank(asset.getNotes())
        );
    }

    private String financeText(Asset asset, List<AssetEvent> events) {
        StringBuilder builder = new StringBuilder();
        builder.append("Financial Links").append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Paying account: ").append(orBlank(asset.getAccountName())).append(System.lineSeparator());
        builder.append("Budget: ").append(orBlank(asset.getBudgetName())).append(System.lineSeparator());
        builder.append("Project: ").append(orBlank(asset.getProjectName())).append(System.lineSeparator());
        builder.append("Project activity: ").append(orBlank(asset.getProjectActivityName())).append(System.lineSeparator());
        builder.append("Purchase transaction: ").append(asset.getPurchaseTransactionId() == null ? "None" : asset.getPurchaseTransactionId()).append(System.lineSeparator());
        builder.append("Payment treatment: ").append(displayStatus(asset.getPaymentTreatment())).append(System.lineSeparator()).append(System.lineSeparator());
        builder.append("Disposal and Valuation Evidence").append(System.lineSeparator()).append(System.lineSeparator());
        for (AssetEvent event : events) {
            if (Set.of("SALE", "PARTIAL_SALE", "VALUE_UPDATE", "MAINTENANCE", "MAINTENANCE_CAPITALIZED", "LINK_PURCHASE_TRANSACTION")
                    .contains(event.getEventType())) {
                builder.append(event.getEventDate())
                        .append(" - ")
                        .append(displayStatus(event.getEventType()))
                        .append(" - ")
                        .append(format(event.getCurrency(), event.getAmount()))
                        .append(System.lineSeparator());
                if (event.getTransactionId() != null) {
                    builder.append("Transaction: ").append(event.getTransactionId()).append(System.lineSeparator());
                }
                builder.append(orBlank(event.getReason())).append(System.lineSeparator());
                if (event.getNotes() != null && !event.getNotes().isBlank()) {
                    builder.append(event.getNotes()).append(System.lineSeparator());
                }
                builder.append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private String historyText(List<AssetEvent> events) {
        if (events.isEmpty()) {
            return "No asset history has been recorded yet.";
        }
        StringBuilder builder = new StringBuilder();
        for (AssetEvent event : events) {
            builder.append(event.getEventDate())
                    .append(" - ")
                    .append(displayStatus(event.getEventType()))
                    .append(System.lineSeparator());
            builder.append("Amount: ").append(format(event.getCurrency(), event.getAmount())).append(System.lineSeparator());
            if (event.getCounterparty() != null && !event.getCounterparty().isBlank()) {
                builder.append("Counterparty: ").append(event.getCounterparty()).append(System.lineSeparator());
            }
            if (event.getPaymentStatus() != null && !event.getPaymentStatus().isBlank()) {
                builder.append("Payment status: ").append(displayStatus(event.getPaymentStatus())).append(System.lineSeparator());
            }
            if (event.getReferenceNumber() != null && !event.getReferenceNumber().isBlank()) {
                builder.append("Reference: ").append(event.getReferenceNumber()).append(System.lineSeparator());
            }
            builder.append(orBlank(event.getReason())).append(System.lineSeparator());
            if (event.getNotes() != null && !event.getNotes().isBlank()) {
                builder.append(event.getNotes()).append(System.lineSeparator());
            }
            builder.append(System.lineSeparator());
        }
        return builder.toString();
    }

    private Asset selectedAsset() {
        Asset selected = assetsTable.getSelectionModel().getSelectedItem();
        if (selected == null && openedAsset != null) {
            return openedAsset;
        }
        if (selected == null) {
            throw new IllegalArgumentException("Select an asset first.");
        }
        return selected;
    }

    private List<Account> activeAccounts() {
        return database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .collect(Collectors.toList());
    }

    private Dialog<ButtonType> dialog(String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(new ButtonType("Save", ButtonBar.ButtonData.OK_DONE), ButtonType.CANCEL);
        return dialog;
    }

    private GridPane dialogGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(12));
        return grid;
    }

    private TextArea notesField() {
        TextArea area = new TextArea();
        area.setPrefRowCount(3);
        area.setWrapText(true);
        return area;
    }

    private void addRow(GridPane grid, int row, String label, javafx.scene.Node node) {
        grid.add(new Label(label), 0, row);
        grid.add(node, 1, row);
    }

    private double parseAmount(TextField field, String label) {
        String raw = field.getText();
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            double value = Double.parseDouble(raw.replace(",", "").trim());
            if (value < 0) {
                throw new IllegalArgumentException(label + " cannot be negative.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    private int parseInt(TextField field, String label) {
        String raw = field.getText();
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a whole number.");
        }
    }

    private String searchableText(Asset asset) {
        return String.join(" ",
                orBlank(asset.getAssetName()),
                orBlank(asset.getAssetCategory()),
                orBlank(asset.getLocation()),
                orBlank(asset.getSupplier()),
                orBlank(asset.getProjectName()),
                orBlank(asset.getBudgetName()),
                orBlank(asset.getSerialNumber()),
                displayStatus(asset.getStatus())
        ).toLowerCase(Locale.ENGLISH);
    }

    private String linkSummary(Asset asset) {
        if (asset.getPurchaseTransactionId() != null) {
            return "Txn " + asset.getPurchaseTransactionId();
        }
        return displayStatus(asset.getPaymentTreatment());
    }

    private String displayStatus(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String value = status.trim().replace('_', ' ').toLowerCase(Locale.ENGLISH);
        StringBuilder builder = new StringBuilder();
        for (String part : value.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    private String format(String currency, double amount) {
        if ("MWK".equalsIgnoreCase(currency)) {
            return MoneyUtil.mwk(amount);
        }
        return (currency == null || currency.isBlank() ? "MWK" : currency) + " " + String.format(Locale.US, "%,.2f", amount);
    }

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005) {
            return String.format(Locale.US, "%,.0f", value);
        }
        return String.format(Locale.US, "%,.2f", value);
    }

    private String orBlank(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String orEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
