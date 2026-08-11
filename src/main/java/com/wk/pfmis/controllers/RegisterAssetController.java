package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public class RegisterAssetController {
    @FXML private TextField assetNameField;
    @FXML private ComboBox<String> assetCategoryBox;
    @FXML private ComboBox<String> acquisitionMethodBox;
    @FXML private DatePicker purchaseDatePicker;
    @FXML private TextField purchaseCostField;
    @FXML private TextField capitalizedCostsField;
    @FXML private ComboBox<String> currencyBox;
    @FXML private ComboBox<Account> payingAccountBox;
    @FXML private ComboBox<String> paymentTreatmentBox;
    @FXML private TextField transactionIdField;
    @FXML private VBox transactionIdSection;
    @FXML private VBox payingAccountSection;
    @FXML private TextField supplierField;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private TextField referenceNumberField;
    @FXML private TextField serialNumberField;
    @FXML private TextField locationField;
    @FXML private ComboBox<String> conditionBox;
    @FXML private TextField quantityField;
    @FXML private TextField supportingDocumentField;
    @FXML private TextArea notesArea;
    @FXML private TextArea resultArea;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        assetCategoryBox.setItems(FXCollections.observableArrayList(
                "Vehicle", "Equipment", "Furniture", "Electronics", "Property", "Livestock",
                "Investment", "Household", "Business", "Tools", "Other"
        ));
        acquisitionMethodBox.setItems(FXCollections.observableArrayList(
                "Purchased", "Gift or Donation", "Inherited", "Opening Asset", "Transferred In", "Constructed", "Other"
        ));
        paymentTreatmentBox.setItems(FXCollections.observableArrayList(
                "Link an existing transaction",
                "Create a new payment transaction",
                "The asset was purchased before PFMIS tracking began"
        ));
        conditionBox.setItems(FXCollections.observableArrayList(
                "New", "Good", "Fair", "Under Maintenance", "Damaged"
        ));
        currencyBox.setItems(FXCollections.observableArrayList(database.listCurrencySuggestions()));
        paymentMethodBox.setItems(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        refreshLinkedData();
        paymentTreatmentBox.valueProperty().addListener((observable, oldValue, newValue) -> updatePaymentTreatmentPrompt());
        clearForm();
        applyRequestedAssetRegistrationContext();
    }

    @FXML
    private void registerAsset() {
        try {
            String assetName = required(assetNameField, "Asset name is required.");
            String category = selected(assetCategoryBox, "Other");
            String acquisitionMethod = selected(acquisitionMethodBox, "Purchased");
            String paymentTreatment = selected(paymentTreatmentBox, "The asset was purchased before PFMIS tracking began");
            LocalDate purchaseDate = requiredDate();
            double unitPurchaseCost = requiredAmount(purchaseCostField, "Purchase cost");
            double unitCapitalizedCosts = amount(capitalizedCostsField, "Capitalized costs");
            int quantity = quantity();
            String currency = currencyCode();
            Account account = payingAccountBox.getValue();
            Integer transactionId = transactionId(paymentTreatment);

            List<Integer> assetIds = database.registerAssets(
                    assetName,
                    category,
                    acquisitionMethod,
                    purchaseDate.toString(),
                    unitPurchaseCost,
                    unitCapitalizedCosts,
                    currency,
                    idOf(account),
                    null,
                    null,
                    null,
                    transactionId,
                    paymentTreatment,
                    text(supplierField),
                    selected(paymentMethodBox, ""),
                    text(referenceNumberField),
                    text(serialNumberField),
                    text(locationField),
                    selected(conditionBox, "Good"),
                    quantity,
                    text(supportingDocumentField),
                    notesArea.getText()
            );

            StringBuilder result = new StringBuilder();
            result.append("Asset registration completed.").append(System.lineSeparator()).append(System.lineSeparator());
            result.append("Created asset records: ").append(assetIds).append(System.lineSeparator());
            result.append("Unit asset value: ").append(format(currency, unitPurchaseCost + unitCapitalizedCosts)).append(System.lineSeparator());
            result.append("Total allocated asset value: ").append(format(currency, (unitPurchaseCost + unitCapitalizedCosts) * quantity)).append(System.lineSeparator());
            if (transactionId != null) {
                result.append("Linked purchase transaction: ").append(transactionId).append(System.lineSeparator());
                result.append("Unallocated purchase amount after registration: ")
                        .append(format(currency, database.assetPurchaseUnallocatedAmount(transactionId))).append(System.lineSeparator());
                result.append("No second payment was created for the linked transaction.").append(System.lineSeparator());
            }
            if (paymentTreatment.toLowerCase(Locale.ENGLISH).contains("create")) {
                result.append("A new payment transaction was created and linked to the asset record(s).").append(System.lineSeparator());
            }
            if (paymentTreatment.toLowerCase(Locale.ENGLISH).contains("before")) {
                result.append("The asset was registered as an opening asset without reducing current-period account balances.").append(System.lineSeparator());
            }
            resultArea.setText(result.toString());
            clearEntryFieldsAfterSave();
        } catch (RuntimeException exception) {
            UiAlerts.error("Asset registration failed", exception);
        }
    }

    @FXML
    private void clearForm() {
        assetNameField.clear();
        assetCategoryBox.setValue("Household");
        acquisitionMethodBox.setValue("Purchased");
        purchaseDatePicker.setValue(LocalDate.now());
        purchaseCostField.setText("0");
        capitalizedCostsField.setText("0");
        currencyBox.setValue(currencyBox.getItems().isEmpty() ? "MWK" : currencyBox.getItems().get(0));
        payingAccountBox.getSelectionModel().clearSelection();
        paymentTreatmentBox.setValue("The asset was purchased before PFMIS tracking began");
        transactionIdField.clear();
        supplierField.clear();
        paymentMethodBox.setValue(paymentMethodBox.getItems().isEmpty() ? "" : paymentMethodBox.getItems().get(0));
        referenceNumberField.clear();
        serialNumberField.clear();
        locationField.clear();
        conditionBox.setValue("Good");
        quantityField.setText("1");
        supportingDocumentField.clear();
        notesArea.clear();
        updatePaymentTreatmentPrompt();
    }

    private void applyRequestedAssetRegistrationContext() {
        NavigationBus.AssetRegistrationContext context = NavigationBus.consumeRequestedAssetRegistrationContext();
        if (context == null) {
            return;
        }
        if (context.sourceName() != null && !context.sourceName().isBlank()) {
            assetNameField.setText(context.sourceName());
        }
        acquisitionMethodBox.setValue("Purchased");
        paymentTreatmentBox.setValue("Link an existing transaction");
        updatePaymentTreatmentPrompt();
        String source = context.sourceType() == null || context.sourceType().isBlank() ? "Source" : context.sourceType();
        String idText = context.sourceId() == null ? "" : " #" + context.sourceId();
        String guidance = context.guidance() == null || context.guidance().isBlank() ? "" : "\n\n" + context.guidance();
        notesArea.setText(source + idText + " handoff from planning workflow." + guidance);
        resultArea.setText(source + " selected for asset recognition review.\n\n"
                + "This page registers an asset only after acquisition details and payment treatment are valid. "
                + "Use an existing posted purchase transaction where money already moved, create a new payment transaction when buying now, "
                + "or use opening/non-cash treatment when no current cash movement should be posted.");
    }

    @FXML
    private void refreshLinkedData() {
        List<Account> activeAccounts = database.listAccounts().stream()
                .filter(account -> "ACTIVE".equalsIgnoreCase(account.getStatus()))
                .toList();
        payingAccountBox.setItems(FXCollections.observableArrayList(activeAccounts));
    }

    private void updatePaymentTreatmentPrompt() {
        String value = selected(paymentTreatmentBox, "");
        boolean linkExisting = value.toLowerCase(Locale.ENGLISH).contains("existing");
        boolean createPayment = value.toLowerCase(Locale.ENGLISH).contains("create");
        transactionIdField.setDisable(!linkExisting);
        transactionIdField.setPromptText(linkExisting ? "Required existing transaction ID" : "Not used for this payment treatment");
        if (transactionIdSection != null) {
            transactionIdSection.setVisible(linkExisting);
            transactionIdSection.setManaged(linkExisting);
        }
        payingAccountBox.setDisable(!createPayment);
        if (payingAccountSection != null) {
            payingAccountSection.setVisible(createPayment);
            payingAccountSection.setManaged(createPayment);
        }
    }

    private void clearEntryFieldsAfterSave() {
        assetNameField.clear();
        serialNumberField.clear();
        supportingDocumentField.clear();
        notesArea.clear();
    }

    private String required(TextField field, String message) {
        String value = field.getText();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String text(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String selected(ComboBox<String> comboBox, String fallback) {
        String value = comboBox.getValue();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private LocalDate requiredDate() {
        LocalDate value = purchaseDatePicker.getValue();
        if (value == null) {
            throw new IllegalArgumentException("Acquisition date is required.");
        }
        return value;
    }

    private double amount(TextField field, String label) {
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

    private double requiredAmount(TextField field, String label) {
        String raw = field.getText();
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return amount(field, label);
    }

    private int quantity() {
        String raw = quantityField.getText();
        if (raw == null || raw.trim().isEmpty()) {
            return 1;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            if (value < 1) {
                throw new IllegalArgumentException("Quantity must be at least 1.");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Quantity must be a whole number.");
        }
    }

    private String currencyCode() {
        String value = selected(currencyBox, "MWK");
        int separator = value.indexOf(" - ");
        return (separator > 0 ? value.substring(0, separator) : value).trim().toUpperCase(Locale.ENGLISH);
    }

    private Integer transactionId(String paymentTreatment) {
        if (!paymentTreatment.toLowerCase(Locale.ENGLISH).contains("existing")) {
            return null;
        }
        String raw = transactionIdField.getText();
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException("Enter the existing purchase transaction ID.");
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Existing transaction ID must be a whole number.");
        }
    }

    private Integer idOf(Account account) {
        return account == null ? null : account.getId();
    }

    private String format(String currency, double amount) {
        if ("MWK".equalsIgnoreCase(currency)) {
            return MoneyUtil.mwk(amount);
        }
        return currency + " " + String.format(Locale.US, "%,.2f", amount);
    }
}
