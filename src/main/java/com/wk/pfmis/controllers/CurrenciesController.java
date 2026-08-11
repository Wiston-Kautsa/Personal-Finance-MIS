package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.fx.ExchangeRateQuote;
import com.wk.pfmis.fx.ExchangeRateService;
import com.wk.pfmis.fx.ExchangeRateService.ExchangeRateSystemStatus;
import com.wk.pfmis.fx.ExchangeRateSource;
import com.wk.pfmis.models.CurrencyRecord;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class CurrenciesController {
    @FXML private Label fxStatusTitleLabel;
    @FXML private Label fxStatusDetailLabel;
    @FXML private Label baseCurrencySummaryLabel;
    @FXML private Label lastUpdateSummaryLabel;
    @FXML private Label activeRatesSummaryLabel;
    @FXML private Label rateSourceSummaryLabel;
    @FXML private Button refreshRatesButton;
    @FXML private TextField currencyNameField;
    @FXML private TextField currencyCodeField;
    @FXML private TextField symbolField;
    @FXML private TextField rateToBaseField;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextField searchField;
    @FXML private ComboBox<String> rateStatusFilterBox;
    @FXML private Label statusLabel;
    @FXML private TableView<CurrencyRecord> currenciesTable;
    @FXML private TableColumn<CurrencyRecord, String> currencyNameColumn;
    @FXML private TableColumn<CurrencyRecord, String> currencyCodeColumn;
    @FXML private TableColumn<CurrencyRecord, String> symbolColumn;
    @FXML private TableColumn<CurrencyRecord, String> rateToBaseColumn;
    @FXML private TableColumn<CurrencyRecord, String> currencyStatusColumn;
    @FXML private TableColumn<CurrencyRecord, String> updatedAtColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final ExchangeRateService exchangeRateService = ExchangeRateService.getInstance();
    private final Map<String, ExchangeRateQuote> latestRates = new LinkedHashMap<>();
    private List<CurrencyRecord> allCurrencies = List.of();

    @FXML
    public void initialize() {
        currencyNameColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCurrencyName()));
        currencyCodeColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCurrencyCode()));
        symbolColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSymbol()));
        rateToBaseColumn.setCellValueFactory(cell -> new SimpleStringProperty(rateDisplay(cell.getValue())));
        currencyStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(rateSourceDisplay(cell.getValue())));
        updatedAtColumn.setCellValueFactory(cell -> new SimpleStringProperty(rateUpdatedDisplay(cell.getValue())));
        statusBox.setItems(FXCollections.observableArrayList("Active", "Base Currency", "Inactive"));
        statusBox.getSelectionModel().select("Active");
        if (rateStatusFilterBox != null) {
            rateStatusFilterBox.setItems(FXCollections.observableArrayList("All", "Current", "Cached", "Manual", "Stale", "Unavailable"));
            rateStatusFilterBox.getSelectionModel().select("All");
            rateStatusFilterBox.valueProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        }
        if (searchField != null) {
            searchField.textProperty().addListener((observable, oldValue, newValue) -> applyFilters());
        }
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
        refreshAllRates();
    }

    @FXML
    private void refreshAllRates() {
        if (refreshRatesButton != null) {
            refreshRatesButton.setDisable(true);
            refreshRatesButton.setText("Updating...");
        }
        statusLabel.setText("Updating exchange rates...");
        exchangeRateService.refreshRatesAsync().whenComplete((quotes, throwable) -> Platform.runLater(() -> {
            if (refreshRatesButton != null) {
                refreshRatesButton.setDisable(false);
                refreshRatesButton.setText("Refresh Rates");
            }
            if (throwable == null) {
                statusLabel.setText("Exchange rates updated successfully.");
                refresh();
                DataRefreshBus.notifyDataChanged();
            } else {
                statusLabel.setText("Unable to reach the exchange-rate provider. PFMIS is using saved rates.");
                refresh();
            }
        }));
    }

    @FXML
    private void addManualRate() {
        showManualRateDialog(currenciesTable.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void showProviderSettings() {
        ExchangeRateSystemStatus status = exchangeRateService.getSystemStatus();
        UiAlerts.info("Automatic Rates: " + (status.enabled() ? "Enabled" : "Disabled")
                + "\nProvider: " + status.providerName()
                + "\nBase Currency: " + status.baseCurrency()
                + "\nRates: " + status.status()
                + "\n" + status.message());
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
        latestRates.clear();
        latestRates.putAll(database.latestExchangeRateMapToBase());
        allCurrencies = database.listCurrencies();
        applyFilters();
        updateStatusSummary();
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
            items.add(TableActions.menuItem("Refresh Rate", () -> refreshSelectedRate(currency)));
            items.add(TableActions.menuItem("View Details", () -> showRateDetails(currency)));
            items.add(TableActions.menuItem("View History", () -> showRateHistory(currency)));
            items.add(TableActions.menuItem("Enter Manual Override", () -> showManualRateDialog(currency)));
            items.add(TableActions.separator());
        }
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

    private void refreshSelectedRate(CurrencyRecord currency) {
        if (currency == null || currency.isBaseCurrency()) {
            UiAlerts.info("Select a non-base currency to refresh.");
            return;
        }
        String base = database.getBaseCurrencyCode();
        statusLabel.setText("Updating " + currency.getCurrencyCode() + "/" + base + "...");
        exchangeRateService.refreshRateAsync(currency.getCurrencyCode(), base)
                .whenComplete((quote, throwable) -> Platform.runLater(() -> {
                    if (throwable == null) {
                        statusLabel.setText(currency.getCurrencyCode() + " exchange rate updated.");
                        refresh();
                        DataRefreshBus.notifyDataChanged();
                    } else {
                        statusLabel.setText("Unable to update " + currency.getCurrencyCode() + ". Saved rates remain available.");
                    }
                }));
    }

    private void showManualRateDialog(CurrencyRecord selected) {
        Dialog<ExchangeRateQuote> dialog = new Dialog<>();
        dialog.setTitle("Enter Manual Exchange Rate");
        dialog.setHeaderText("Manual rates override automatic rates according to your exchange-rate settings.");
        ComboBox<String> fromBox = new ComboBox<>(FXCollections.observableArrayList(currencyCodes()));
        ComboBox<String> toBox = new ComboBox<>(FXCollections.observableArrayList(currencyCodes()));
        fromBox.setValue(selected == null || selected.isBaseCurrency() ? "" : selected.getCurrencyCode());
        toBox.setValue(database.getBaseCurrencyCode());
        TextField rateField = new TextField();
        rateField.setPromptText("Exchange rate");
        DatePicker effectiveDate = new DatePicker(LocalDate.now());
        DatePicker expiryDate = new DatePicker();
        TextArea notes = new TextArea();
        notes.setPromptText("Reason / Notes");
        notes.setPrefRowCount(3);
        Label preview = new Label("Enter a rate greater than 0.");
        preview.setWrapText(true);
        rateField.textProperty().addListener((observable, oldValue, newValue) -> updateManualPreview(fromBox, toBox, rateField, preview));
        fromBox.valueProperty().addListener((observable, oldValue, newValue) -> updateManualPreview(fromBox, toBox, rateField, preview));
        toBox.valueProperty().addListener((observable, oldValue, newValue) -> updateManualPreview(fromBox, toBox, rateField, preview));
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("From Currency *"), 0, 0);
        grid.add(fromBox, 1, 0);
        grid.add(new Label("To Currency *"), 0, 1);
        grid.add(toBox, 1, 1);
        grid.add(new Label("Exchange Rate *"), 0, 2);
        grid.add(rateField, 1, 2);
        grid.add(new Label("Effective Date *"), 0, 3);
        grid.add(effectiveDate, 1, 3);
        grid.add(new Label("Expiry Date"), 0, 4);
        grid.add(expiryDate, 1, 4);
        grid.add(new Label("Reason / Notes"), 0, 5);
        grid.add(notes, 1, 5);
        grid.add(preview, 1, 6);
        dialog.getDialogPane().setContent(grid);
        ButtonType save = new ButtonType("Save Manual Rate", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);
        dialog.setResultConverter(button -> {
            if (!save.equals(button)) {
                return null;
            }
            String from = requireCurrency(fromBox.getValue(), "From Currency");
            String to = requireCurrency(toBox.getValue(), "To Currency");
            if (from.equals(to)) {
                throw new IllegalArgumentException("Different currencies are required for a manual exchange rate.");
            }
            return new ExchangeRateQuote(
                    from,
                    to,
                    parsePositiveRate(rateField.getText()),
                    effectiveDate.getValue() == null ? LocalDate.now() : effectiveDate.getValue(),
                    Instant.now(),
                    "PFMIS Manual Rate",
                    ExchangeRateSource.MANUAL,
                    "MANUAL",
                    true,
                    false,
                    notes.getText()
            );
        });
        try {
            Optional<ExchangeRateQuote> result = dialog.showAndWait();
            result.ifPresent(quote -> {
                exchangeRateService.saveManualRate(quote.fromCurrency(), quote.toCurrency(), quote.rate(), quote.effectiveDate(), expiryDate.getValue(), quote.notes());
                statusLabel.setText("Manual exchange rate saved.");
                refresh();
                DataRefreshBus.notifyDataChanged();
            });
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save manual rate", exception);
        }
    }

    private void showRateDetails(CurrencyRecord currency) {
        ExchangeRateQuote quote = latestRates.get(currency.getCurrencyCode());
        if (quote == null) {
            UiAlerts.info("No exchange rate is available for " + currency.getCurrencyCode() + ".");
            return;
        }
        UiAlerts.info("Currency Pair: " + quote.fromCurrency() + " -> " + quote.toCurrency()
                + "\nRate: " + rateText(quote)
                + "\nInverse Rate: " + inverseText(quote)
                + "\nSource: " + quote.providerName()
                + "\nRate Type: " + quote.rateType()
                + "\nRetrieved: " + timestampText(quote.retrievedAt())
                + "\nEffective Date: " + quote.effectiveDate()
                + "\nStatus: " + quote.status());
    }

    private void showRateHistory(CurrencyRecord currency) {
        String base = database.getBaseCurrencyCode();
        List<ExchangeRateQuote> history = database.listExchangeRateHistory(currency.getCurrencyCode(), base, 40);
        if (history.isEmpty()) {
            UiAlerts.info("No exchange-rate history is available for " + currency.getCurrencyCode() + "/" + base + ".");
            return;
        }
        StringBuilder builder = new StringBuilder(currency.getCurrencyCode()).append(" / ").append(base).append("\n\n");
        for (ExchangeRateQuote quote : history) {
            builder.append(timestampText(quote.retrievedAt()))
                    .append(" | ")
                    .append(rateText(quote))
                    .append(" | ")
                    .append(quote.source())
                    .append(" | ")
                    .append(quote.status())
                    .append('\n');
        }
        UiAlerts.info(builder.toString());
    }

    private void applyFilters() {
        String search = searchField == null || searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(Locale.ENGLISH);
        String filter = rateStatusFilterBox == null || rateStatusFilterBox.getValue() == null
                ? "All"
                : rateStatusFilterBox.getValue();
        currenciesTable.setItems(FXCollections.observableArrayList(allCurrencies.stream()
                .filter(currency -> search.isBlank()
                        || safe(currency.getCurrencyCode()).toLowerCase(Locale.ENGLISH).contains(search)
                        || safe(currency.getCurrencyName()).toLowerCase(Locale.ENGLISH).contains(search))
                .filter(currency -> "All".equals(filter) || filter.equalsIgnoreCase(rateStatusDisplay(currency)))
                .toList()));
    }

    private void updateStatusSummary() {
        ExchangeRateSystemStatus status = exchangeRateService.getSystemStatus();
        if (fxStatusTitleLabel != null) {
            fxStatusTitleLabel.setText("Automatic Exchange Rates: " + status.status());
        }
        if (fxStatusDetailLabel != null) {
            fxStatusDetailLabel.setText(status.message());
        }
        if (baseCurrencySummaryLabel != null) {
            baseCurrencySummaryLabel.setText(status.baseCurrency());
        }
        if (lastUpdateSummaryLabel != null) {
            lastUpdateSummaryLabel.setText(status.lastSuccessfulUpdate().map(this::timestampText).orElse("No update yet"));
        }
        if (activeRatesSummaryLabel != null) {
            activeRatesSummaryLabel.setText(String.valueOf(status.activeRateCount()));
        }
        if (rateSourceSummaryLabel != null) {
            rateSourceSummaryLabel.setText(status.providerName());
        }
    }

    private String rateDisplay(CurrencyRecord currency) {
        if (currency.isBaseCurrency()) {
            return "1 " + currency.getCurrencyCode() + " = " + database.getBaseCurrencyCode() + " 1.00";
        }
        ExchangeRateQuote quote = latestRates.get(currency.getCurrencyCode());
        return quote == null ? "Unavailable" : rateText(quote);
    }

    private String rateSourceDisplay(CurrencyRecord currency) {
        if (currency.isBaseCurrency()) {
            return "Base Currency";
        }
        ExchangeRateQuote quote = latestRates.get(currency.getCurrencyCode());
        return quote == null ? "Unavailable" : quote.source().name();
    }

    private String rateUpdatedDisplay(CurrencyRecord currency) {
        ExchangeRateQuote quote = latestRates.get(currency.getCurrencyCode());
        return quote == null ? safe(currency.getUpdatedAt()) : timestampText(quote.retrievedAt());
    }

    private String rateStatusDisplay(CurrencyRecord currency) {
        if (currency.isBaseCurrency()) {
            return "Current";
        }
        ExchangeRateQuote quote = latestRates.get(currency.getCurrencyCode());
        return quote == null ? "Unavailable" : switch (quote.status()) {
            case LIVE, CURRENT -> "Current";
            case CACHED -> "Cached";
            case MANUAL -> "Manual";
            case STALE -> "Stale";
            case UNAVAILABLE, DISABLED -> "Unavailable";
        };
    }

    private List<String> currencyCodes() {
        return database.listCurrencies().stream()
                .map(CurrencyRecord::getCurrencyCode)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    private void updateManualPreview(ComboBox<String> fromBox, ComboBox<String> toBox, TextField rateField, Label preview) {
        try {
            BigDecimal rate = parsePositiveRate(rateField.getText());
            String from = requireCurrency(fromBox.getValue(), "From Currency");
            String to = requireCurrency(toBox.getValue(), "To Currency");
            preview.setText("1 " + from + " = " + to + " " + formatRate(rate)
                    + "\n1 " + to + " approximately " + from + " " + formatRate(BigDecimal.ONE.divide(rate, 10, java.math.RoundingMode.HALF_UP)));
        } catch (RuntimeException exception) {
            preview.setText("Exchange rate must be greater than 0.");
        }
    }

    private BigDecimal parsePositiveRate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Exchange rate must be greater than 0.");
        }
        BigDecimal rate = new BigDecimal(value.replace(",", "").trim());
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be greater than 0.");
        }
        return rate;
    }

    private String requireCurrency(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim().toUpperCase(Locale.ENGLISH);
    }

    private String rateText(ExchangeRateQuote quote) {
        return "1 " + quote.fromCurrency() + " = " + quote.toCurrency() + " " + formatRate(quote.rate());
    }

    private String inverseText(ExchangeRateQuote quote) {
        return "1 " + quote.toCurrency() + " approximately " + quote.fromCurrency() + " "
                + formatRate(BigDecimal.ONE.divide(quote.rate(), 10, java.math.RoundingMode.HALF_UP));
    }

    private String formatRate(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(6);
        return format.format(amount);
    }

    private String timestampText(Instant instant) {
        return instant == null
                ? "-"
                : DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", Locale.ENGLISH)
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
