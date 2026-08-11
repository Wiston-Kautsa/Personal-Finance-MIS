package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.db.DatabaseHandler.DataImportBatchRecord;
import com.wk.pfmis.db.DatabaseHandler.DataManualInputRecord;
import com.wk.pfmis.db.DatabaseHandler.RejectedImportRecord;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.SystemUser;
import com.wk.pfmis.security.UserSession;
import com.wk.pfmis.utils.ExportPathService;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DataIntakeTaskController {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ENGLISH);
    private static final int PAGE_LIMIT = 100;
    private static final List<String> MANUAL_INPUT_TYPES = List.of(
            "Report Note",
            "External Target",
            "Forecast Assumption",
            "Opening Migration Figure",
            "Management Comment",
            "Supporting Indicator"
    );
    private static final Set<String> CALCULATED_TOTALS = Set.of(
            "total income",
            "total expenses",
            "account balance",
            "outstanding loans",
            "budget spending"
    );

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private VBox contentContainer;
    @FXML private TextArea resultArea;
    @FXML private Button supportingActionButton;
    @FXML private Button mainActionButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    private String currentArea = "Manual Inputs";
    private List<Account> accounts = List.of();
    private List<Category> categories = List.of();
    private List<String> paymentMethods = List.of();
    private List<FinanceTransaction> recentTransactions = List.of();
    private List<ImportPreviewRow> previewRows = List.of();
    private List<IntakeRow> currentRows = List.of();
    private Path selectedImportFile;

    private ComboBox<String> inputTypeBox;
    private ComboBox<String> dataTypeBox;
    private ComboBox<String> batchBox;
    private ComboBox<String> reasonBox;
    private ComboBox<String> statusBox;
    private TextField reportingPeriodField;
    private TextField manualDescriptionField;
    private TextField manualValueField;
    private TextField manualSourceField;
    private TextField searchField;
    private TextField fromDateField;
    private TextField toDateField;
    private TextArea manualNotesArea;
    private Label fileNameLabel;
    private Label workspaceLabel;
    private Label rowsFoundLabel;
    private Label validRowsLabel;
    private Label duplicateRowsLabel;
    private Label invalidRowsLabel;
    private TableView<IntakeRow> table;

    @FXML
    public void initialize() {
        selectArea(currentArea);
    }

    public void selectArea(String area) {
        currentArea = area == null || area.isBlank() ? "Manual Inputs" : area.trim();
        render();
    }

    public void refresh() {
        if ("Import File".equals(currentArea) && selectedImportFile != null) {
            parseSelectedImportFile();
            return;
        }
        search();
    }

    @FXML
    private void runSupportingAction() {
        switch (currentArea) {
            case "Import File" -> chooseFile();
            case "Rejected Records" -> openAndFixRejectedRow();
            case "Import History" -> viewImportDetails();
            default -> clearManualInput();
        }
    }

    @FXML
    private void runMainAction() {
        switch (currentArea) {
            case "Import File" -> importValidRecords();
            case "Rejected Records" -> retrySelectedRejectedRows();
            case "Import History" -> exportImportHistory();
            default -> saveManualInput();
        }
    }

    private void render() {
        loadLookupData();
        contentContainer.getChildren().clear();
        resultArea.clear();
        table = null;
        currentRows = List.of();
        configureTitleAndButtons();
        switch (currentArea) {
            case "Import File" -> renderImportFile();
            case "Rejected Records" -> renderRejectedRecords();
            case "Import History" -> renderImportHistory();
            default -> renderManualInputs();
        }
        search();
    }

    private void configureTitleAndButtons() {
        supportingActionButton.setDisable(false);
        mainActionButton.setDisable(false);
        supportingActionButton.getStyleClass().setAll("secondary-button");
        mainActionButton.getStyleClass().setAll("primary-button");
        switch (currentArea) {
            case "Import File" -> {
                titleLabel.setText("Import File");
                subtitleLabel.setText("Choose a CSV statement or transaction file, review the summary, then import the valid rows.");
                supportingActionButton.setText("Choose File");
                mainActionButton.setText("Import Valid Records");
            }
            case "Rejected Records" -> {
                titleLabel.setText("Rejected Records");
                subtitleLabel.setText("Open imported rows that need correction, then retry the selected rows.");
                supportingActionButton.setText("Open and Fix");
                mainActionButton.setText("Retry Selected");
            }
            case "Import History" -> {
                titleLabel.setText("Import History");
                subtitleLabel.setText("Search previous imports, view the batch details or export the filtered history.");
                supportingActionButton.setText("View Details");
                mainActionButton.setText("Export History");
            }
            default -> {
                titleLabel.setText("Manual Inputs");
                subtitleLabel.setText("Enter information that cannot be calculated automatically from normal transactions.");
                supportingActionButton.setText("Clear");
                mainActionButton.setText("Save Input");
            }
        }
    }

    private void renderManualInputs() {
        inputTypeBox = combo(MANUAL_INPUT_TYPES, MANUAL_INPUT_TYPES.getFirst());
        reportingPeriodField = textField("Select period, for example 2026-07");
        reportingPeriodField.setText(YearMonth.now().toString());
        manualDescriptionField = textField("Enter description");
        manualValueField = textField("Enter value");
        manualSourceField = textField("Enter source");
        manualNotesArea = textArea("Optional notes", 3);

        table = table("No manual inputs have been saved.", false, List.of(
                column("Date", 150),
                column("Type", 190),
                column("Period", 130),
                column("Description", 260),
                column("Value", 130),
                column("Source", 180)
        ));

        contentContainer.getChildren().addAll(
                fields(
                        field("Input type", inputTypeBox),
                        field("Reporting period", reportingPeriodField),
                        wideField("Description", manualDescriptionField),
                        field("Value", manualValueField),
                        wideField("Source", manualSourceField)
                ),
                labelledBlock("Notes", manualNotesArea),
                table
        );
        resultArea.setText("Save supporting inputs for reports without overwriting calculated financial totals.");
    }

    private void renderImportFile() {
        dataTypeBox = combo(List.of("Transactions"), "Transactions");
        fileNameLabel = valueLabel(selectedImportFile == null ? "No file selected" : selectedImportFile.getFileName().toString());
        workspaceLabel = valueLabel(workspaceText());
        rowsFoundLabel = valueLabel("0");
        validRowsLabel = valueLabel("0");
        duplicateRowsLabel = valueLabel("0");
        invalidRowsLabel = valueLabel("0");

        table = table("Choose a file to preview rows before importing.", false, List.of(
                column("Row", 80),
                column("Date", 130),
                column("Description", 330),
                column("Amount", 150),
                column("Status", 190)
        ));

        contentContainer.getChildren().addAll(
                fields(
                        field("Data type", dataTypeBox),
                        field("File", fileNameLabel),
                        wideField("Workspace", workspaceLabel)
                ),
                summaryGrid(List.of(
                        summary("Rows found", rowsFoundLabel),
                        summary("Valid rows", validRowsLabel),
                        summary("Possible duplicates", duplicateRowsLabel),
                        summary("Invalid rows", invalidRowsLabel)
                )),
                table
        );
        renderPreviewRows();
        resultArea.setText("Choose a file, review the summary, then import valid records.");
    }

    private void renderRejectedRecords() {
        List<DataImportBatchRecord> batches = database.listDataImportBatches(200);
        List<String> batchOptions = new ArrayList<>();
        batchOptions.add("All batches");
        batches.stream().map(DataImportBatchRecord::batchKey).forEach(batchOptions::add);
        batchBox = combo(batchOptions, "All batches");

        List<String> reasons = new ArrayList<>();
        reasons.add("All reasons");
        database.listRejectedImportRecords(300).stream()
                .map(RejectedImportRecord::problem)
                .map(this::firstProblem)
                .filter(value -> !value.isBlank())
                .distinct()
                .forEach(reasons::add);
        reasonBox = combo(reasons, "All reasons");
        searchField = textField("Description or row number");

        table = table("No rejected import rows were found.", true, List.of(
                column("Row", 80),
                column("Record", 260),
                column("Problem", 270),
                column("Original value", 230),
                column("Status", 170)
        ));
        contentContainer.getChildren().addAll(
                fields(
                        field("Import batch", batchBox),
                        field("Reason", reasonBox),
                        wideField("Search", searchField),
                        searchButton("Search")
                ),
                table
        );
        resultArea.setText("Select a rejected row, then use Open and Fix or Retry Selected.");
    }

    private void renderImportHistory() {
        dataTypeBox = combo(List.of("All types", "Transactions"), "All types");
        statusBox = combo(List.of(
                "All statuses",
                "Ready",
                "Imported",
                "Imported With Rejections",
                "Rejected",
                "Failed",
                "Pending Approval",
                "Cancelled"
        ), "All statuses");
        fromDateField = textField("From");
        toDateField = textField("To");
        searchField = textField("Batch or file");

        table = table("No import history was found for the selected filters.", false, List.of(
                column("Date", 150),
                column("Batch", 180),
                column("File", 250),
                column("Type", 130),
                column("Imported", 100),
                column("Rejected", 100),
                column("User", 180),
                column("Status", 190)
        ));

        contentContainer.getChildren().addAll(
                fields(
                        field("Data type", dataTypeBox),
                        field("Status", statusBox),
                        field("Date from", fromDateField),
                        field("Date to", toDateField),
                        wideField("Search", searchField),
                        searchButton("Search")
                ),
                table
        );
        resultArea.setText("Import History is read-only. Select a batch to view details or export the filtered list.");
    }

    private void loadLookupData() {
        accounts = database.listAccounts();
        categories = database.listCategories();
        paymentMethods = database.listPaymentMethodSuggestions();
        recentTransactions = database.listRecentTransactions(1000);
    }

    private void search() {
        switch (currentArea) {
            case "Import File" -> renderPreviewRows();
            case "Rejected Records" -> searchRejectedRecords();
            case "Import History" -> searchImportHistory();
            default -> searchManualInputs();
        }
    }

    private void searchManualInputs() {
        List<IntakeRow> rows = new ArrayList<>();
        for (DataManualInputRecord input : database.listDataManualInputs(PAGE_LIMIT)) {
            rows.add(new IntakeRow(
                    List.of(
                            safe(input.createdAt()),
                            safe(input.inputType()),
                            safe(input.reportingPeriod()),
                            safe(input.description()),
                            safe(input.inputValue()),
                            safe(input.source())
                    ),
                    input
            ));
        }
        setRows(rows);
    }

    private void renderPreviewRows() {
        if (table == null || !"Import File".equals(currentArea)) {
            return;
        }
        List<IntakeRow> rows = previewRows.stream()
                .map(row -> new IntakeRow(List.of(
                        String.valueOf(row.rowNumber()),
                        safe(row.dateText()),
                        safe(row.description()),
                        row.amountText(),
                        safe(row.status())
                ), row))
                .toList();
        setRows(rows);
        rowsFoundLabel.setText(String.valueOf(previewRows.size()));
        validRowsLabel.setText(String.valueOf(readyRows().size()));
        duplicateRowsLabel.setText(String.valueOf(countStatus("Possible duplicate")));
        invalidRowsLabel.setText(String.valueOf(previewRows.size() - readyRows().size() - countStatus("Possible duplicate")));
        mainActionButton.setDisable(readyRows().isEmpty());
    }

    private void searchRejectedRecords() {
        String selectedBatch = selected(batchBox);
        String selectedReason = selected(reasonBox);
        String search = text(searchField).toLowerCase(Locale.ENGLISH);
        List<IntakeRow> rows = new ArrayList<>();
        for (RejectedImportRecord record : database.listRejectedImportRecords(300)) {
            if (!isAll(selectedBatch) && !selectedBatch.equals(record.batchKey())) {
                continue;
            }
            if (!isAll(selectedReason) && !safe(record.problem()).toLowerCase(Locale.ENGLISH).contains(selectedReason.toLowerCase(Locale.ENGLISH))) {
                continue;
            }
            String searchable = (record.rowNumber() + " " + record.recordText() + " " + record.problem()
                    + " " + record.originalValue() + " " + record.status()).toLowerCase(Locale.ENGLISH);
            if (!search.isBlank() && !searchable.contains(search)) {
                continue;
            }
            rows.add(new IntakeRow(
                    List.of(
                            String.valueOf(record.rowNumber()),
                            recordSummary(record),
                            safe(record.problem()),
                            safe(record.originalValue()),
                            safe(record.status())
                    ),
                    record
            ));
        }
        setRows(rows);
        resultArea.setText(rows.isEmpty()
                ? "No rejected records were found for the selected filters."
                : "Showing " + rows.size() + " rejected record(s).");
    }

    private void searchImportHistory() {
        String selectedType = selected(dataTypeBox);
        String selectedStatus = selected(statusBox);
        LocalDate from = parseOptionalDate(text(fromDateField));
        LocalDate to = parseOptionalDate(text(toDateField));
        String search = text(searchField).toLowerCase(Locale.ENGLISH);

        List<IntakeRow> rows = new ArrayList<>();
        for (DataImportBatchRecord batch : database.listDataImportBatches(300)) {
            if (!isAll(selectedType) && !safe(batch.dataType()).equalsIgnoreCase(selectedType)) {
                continue;
            }
            if (!isAll(selectedStatus) && !safe(batch.status()).equalsIgnoreCase(selectedStatus)) {
                continue;
            }
            LocalDate createdDate = parseCreatedDate(batch.createdAt());
            if (from != null && createdDate != null && createdDate.isBefore(from)) {
                continue;
            }
            if (to != null && createdDate != null && createdDate.isAfter(to)) {
                continue;
            }
            String searchable = (batch.batchKey() + " " + batch.originalFilename() + " " + batch.status()).toLowerCase(Locale.ENGLISH);
            if (!search.isBlank() && !searchable.contains(search)) {
                continue;
            }
            rows.add(new IntakeRow(
                    List.of(
                            safe(batch.createdAt()),
                            safe(batch.batchKey()),
                            safe(batch.originalFilename()),
                            safe(batch.dataType()),
                            String.valueOf(batch.importedRows()),
                            String.valueOf(batch.rejectedRows()),
                            safe(batch.importedBy()),
                            safe(batch.status())
                    ),
                    batch
            ));
        }
        setRows(rows);
    }

    private void setRows(List<IntakeRow> rows) {
        currentRows = rows;
        if (table != null) {
            table.setItems(FXCollections.observableArrayList(rows));
        }
    }

    private void saveManualInput() {
        try {
            String type = selected(inputTypeBox);
            String description = text(manualDescriptionField);
            if (mentionsCalculatedTotal(type) || mentionsCalculatedTotal(description)) {
                resultArea.setText("Manual inputs cannot overwrite calculated financial totals.");
                return;
            }
            database.saveDataManualInput(
                    type,
                    text(reportingPeriodField),
                    description,
                    text(manualValueField),
                    text(manualSourceField),
                    manualNotesArea == null ? "" : manualNotesArea.getText()
            );
            clearManualInput();
            searchManualInputs();
            DataRefreshBus.notifyDataChanged();
            resultArea.setText("Input saved successfully and will be available in the selected report period.");
        } catch (RuntimeException exception) {
            resultArea.setText("Manual Inputs could not be saved.\n\nNo data was changed.\nReason: " + rootMessage(exception));
            database.recordSystemLog("Data Intake", "Save Manual Input Failed", "ERROR", rootMessage(exception));
        }
    }

    private void clearManualInput() {
        if (inputTypeBox != null && !inputTypeBox.getItems().isEmpty()) {
            inputTypeBox.getSelectionModel().selectFirst();
        }
        if (reportingPeriodField != null) {
            reportingPeriodField.setText(YearMonth.now().toString());
        }
        clear(manualDescriptionField);
        clear(manualValueField);
        clear(manualSourceField);
        if (manualNotesArea != null) {
            manualNotesArea.clear();
        }
        resultArea.setText("Manual input fields cleared.");
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Import File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("CSV and text files", "*.csv", "*.txt"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File selected = chooser.showOpenDialog(ownerWindow());
        if (selected == null) {
            return;
        }
        selectedImportFile = selected.toPath().toAbsolutePath().normalize();
        parseSelectedImportFile();
    }

    private void parseSelectedImportFile() {
        try {
            previewRows = parseImportFile(selectedImportFile);
            if (fileNameLabel != null) {
                fileNameLabel.setText(selectedImportFile.getFileName().toString());
            }
            renderPreviewRows();
            resultArea.setText("File reviewed. Import Valid Records will save only rows marked Ready.");
        } catch (RuntimeException exception) {
            String reference = "ERR-DI-" + LocalDateTime.now().format(FILE_TIMESTAMP);
            previewRows = List.of();
            renderPreviewRows();
            resultArea.setText("Import File could not be opened.\n\nNo data was changed.\nReference: " + reference + "\nReason: " + userSafeMessage(exception));
            database.recordSystemLog(
                    "Data Intake",
                    "Import File Failed",
                    "ERROR",
                    "Reference: " + reference
                            + "\nFile: " + (selectedImportFile == null ? "" : selectedImportFile.getFileName())
                            + "\nException type: " + exception.getClass().getName()
                            + "\nMessage: " + rootMessage(exception)
            );
        }
    }

    private List<ImportPreviewRow> parseImportFile(Path file) {
        if (file == null) {
            return List.of();
        }
        String lowerName = file.getFileName().toString().toLowerCase(Locale.ENGLISH);
        if (!lowerName.endsWith(".csv") && !lowerName.endsWith(".txt")) {
            throw new IllegalArgumentException("Choose a CSV or text statement file.");
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("The selected file could not be read.", exception);
        }
        if (lines.isEmpty()) {
            return List.of();
        }
        List<String> headers = parseCsvLine(stripBom(lines.getFirst()));
        Map<String, Integer> headerIndex = headerIndex(headers);
        List<ImportPreviewRow> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line == null || line.isBlank()) {
                continue;
            }
            rows.add(previewRow(index + 1, parseCsvLine(line), headerIndex, line));
        }
        return rows;
    }

    private ImportPreviewRow previewRow(int rowNumber, List<String> values, Map<String, Integer> headers, String rawLine) {
        String dateText = value(values, headers, "date", "transaction date", "posted date", "value date");
        String description = value(values, headers, "description", "narration", "details", "memo");
        String amountText = value(values, headers, "amount", "value", "transaction amount");
        String accountText = value(values, headers, "account", "account name", "wallet", "source account");
        String categoryText = value(values, headers, "category", "category name");
        String paymentText = value(values, headers, "payment method", "method", "channel");
        String reference = value(values, headers, "reference", "reference number", "ref", "transaction id");
        String typeText = value(values, headers, "type", "transaction type", "debit credit", "direction");

        List<String> problems = new ArrayList<>();
        LocalDate date = null;
        Double amount = null;
        String transactionType = null;
        Account account = null;
        Category category = null;

        if (dateText.isBlank()) {
            problems.add("Date is missing");
        } else {
            date = parseFlexibleDate(dateText);
            if (date == null) {
                problems.add("Invalid date");
            }
        }

        if (description.isBlank()) {
            problems.add("Description is missing");
        }

        if (amountText.isBlank()) {
            problems.add("Amount is missing");
        } else {
            amount = parseAmount(amountText);
            if (amount == null || Math.abs(amount) < 0.005) {
                problems.add("Invalid amount");
            }
        }

        if (amount != null) {
            transactionType = normalizeTransactionType(typeText, amount);
            if (transactionType == null) {
                problems.add("Invalid transaction type");
            } else if ("TRANSFER".equals(transactionType)) {
                problems.add("Transfers need source and destination accounts");
            }
        }

        if (accountText.isBlank()) {
            problems.add("Account is missing");
        } else {
            account = findAccount(accountText);
            if (account == null) {
                problems.add("Account was not found");
            } else if (!"ACTIVE".equalsIgnoreCase(safe(account.getStatus(), "ACTIVE"))) {
                problems.add("Account is not active");
            }
        }

        if (categoryText.isBlank()) {
            problems.add("Category is missing");
        } else if (transactionType != null && !"TRANSFER".equals(transactionType)) {
            category = findCategory(categoryText, transactionType);
            if (category == null) {
                problems.add("Category was not found");
            }
        }

        if (paymentText.isBlank()) {
            problems.add("Payment method is missing");
        } else if (paymentMethods.stream().noneMatch(method -> method.equalsIgnoreCase(paymentText.trim()))) {
            problems.add("Payment method was not found");
        }

        String status = statusForProblems(problems);
        if (problems.isEmpty() && isPossibleDuplicate(date, account, transactionType, Math.abs(amount), description)) {
            status = "Possible duplicate";
            problems.add("Possible duplicate");
        }

        return new ImportPreviewRow(
                rowNumber,
                dateText,
                description,
                amountText,
                accountText,
                categoryText,
                paymentText,
                reference,
                typeText,
                date,
                amount == null ? null : Math.abs(amount),
                transactionType,
                account == null ? null : account.getId(),
                category == null ? null : category.getId(),
                status,
                String.join("; ", problems),
                rawLine
        );
    }

    private void importValidRecords() {
        List<ImportPreviewRow> rowsToImport = readyRows();
        if (rowsToImport.isEmpty()) {
            resultArea.setText("No rows are ready to import.");
            return;
        }
        int duplicateCount = countStatus("Possible duplicate");
        int rejectedCount = previewRows.size() - rowsToImport.size();
        String message = lines(
                "Valid records:       " + rowsToImport.size(),
                "Possible duplicates: " + duplicateCount,
                "Rejected records:    " + rejectedCount,
                "",
                "Only valid records will be imported.",
                "Rejected records can be reviewed later."
        );
        if (!confirm("Import these records?", message, "Confirm Import")) {
            return;
        }

        String batchKey = "IMP-" + LocalDateTime.now().format(FILE_TIMESTAMP);
        List<ImportPreviewRow> rejectedRows = new ArrayList<>(previewRows.stream()
                .filter(row -> !"Ready".equals(row.status()))
                .toList());
        int importedRows = 0;
        double importedTotal = 0;
        for (ImportPreviewRow row : rowsToImport) {
            try {
                database.recordTransaction(
                        row.accountId(),
                        row.categoryId(),
                        null,
                        null,
                        row.transactionType(),
                        "NORMAL",
                        "COMPLETED",
                        row.amount(),
                        row.date(),
                        row.description(),
                        row.paymentMethod(),
                        row.reference()
                );
                importedRows++;
                importedTotal += row.amount();
            } catch (RuntimeException exception) {
                rejectedRows.add(row.withStatus("Failed", userSafeMessage(exception)));
            }
        }

        String status = importStatus(importedRows, rejectedRows.size());
        int batchId = database.createDataImportBatch(
                batchKey,
                selected(dataTypeBox),
                selectedImportFile == null ? "Selected file" : selectedImportFile.getFileName().toString(),
                checksumOrBlank(selectedImportFile),
                previewRows.size(),
                rowsToImport.size(),
                duplicateCount,
                rejectedRows.size(),
                importedRows,
                importedTotal,
                status
        );
        for (ImportPreviewRow row : rejectedRows) {
            database.recordRejectedImportRow(
                    batchId,
                    row.rowNumber(),
                    recordText(row),
                    row.problem(),
                    row.originalValue(),
                    "Imported".equals(status) ? "Imported" : "Needs correction",
                    row.dateText(),
                    row.description(),
                    row.amount(),
                    row.accountName(),
                    row.categoryName(),
                    row.paymentMethod(),
                    row.reference(),
                    row.transactionType()
            );
        }

        previewRows = List.of();
        selectedImportFile = null;
        contentContainer.getChildren().clear();
        table = null;
        currentRows = List.of();
        renderImportFile();
        DataRefreshBus.notifyDataChanged();
        resultArea.setText(lines(
                "Import completed.",
                "",
                "Batch: " + batchKey,
                "Imported records: " + importedRows,
                "Rejected records: " + rejectedRows.size()
        ));
    }

    private void openAndFixRejectedRow() {
        RejectedImportRecord record = selectedRejectedRecord();
        if (record == null) {
            resultArea.setText("Select a rejected record to correct.");
            return;
        }
        Optional<CorrectionValues> correction = correctionDialog(record);
        if (correction.isEmpty()) {
            return;
        }
        CorrectionValues value = correction.get();
        database.updateRejectedImportCorrection(
                record.id(),
                value.date(),
                value.description(),
                value.amount(),
                value.account(),
                value.category(),
                value.paymentMethod(),
                value.reference(),
                value.type()
        );
        searchRejectedRecords();
        resultArea.setText("Correction saved. Select Retry Selected to import when valid.");
    }

    private void retrySelectedRejectedRows() {
        List<RejectedImportRecord> selected = selectedRejectedRecords();
        if (selected.isEmpty()) {
            resultArea.setText("Select at least one rejected record to retry.");
            return;
        }
        int imported = 0;
        int stillNeedsCorrection = 0;
        for (RejectedImportRecord record : selected) {
            if ("Imported".equalsIgnoreCase(safe(record.status()))) {
                continue;
            }
            ImportPreviewRow corrected = previewRowFromRejected(record);
            if (!"Ready".equals(corrected.status())) {
                stillNeedsCorrection++;
                continue;
            }
            try {
                database.recordTransaction(
                        corrected.accountId(),
                        corrected.categoryId(),
                        null,
                        null,
                        corrected.transactionType(),
                        "NORMAL",
                        "COMPLETED",
                        corrected.amount(),
                        corrected.date(),
                        corrected.description(),
                        corrected.paymentMethod(),
                        corrected.reference()
                );
                database.markRejectedImportRecordImported(record.id());
                imported++;
            } catch (RuntimeException exception) {
                stillNeedsCorrection++;
                database.recordSystemLog("Data Intake", "Retry Rejected Row Failed", "ERROR", rootMessage(exception));
            }
        }
        searchRejectedRecords();
        DataRefreshBus.notifyDataChanged();
        resultArea.setText(lines(
                imported + " record(s) were imported successfully.",
                stillNeedsCorrection + " record(s) still require correction."
        ));
    }

    private ImportPreviewRow previewRowFromRejected(RejectedImportRecord record) {
        String date = firstNonBlank(record.correctedDate(), detailValue(record.recordText(), "Date"));
        String description = firstNonBlank(record.correctedDescription(), detailValue(record.recordText(), "Description"));
        String amount = record.correctedAmount() == null
                ? detailValue(record.recordText(), "Amount")
                : String.valueOf(record.correctedAmount());
        String account = firstNonBlank(record.correctedAccount(), detailValue(record.recordText(), "Account"));
        String category = firstNonBlank(record.correctedCategory(), detailValue(record.recordText(), "Category"));
        String payment = firstNonBlank(record.correctedPaymentMethod(), detailValue(record.recordText(), "Payment method"));
        String reference = firstNonBlank(record.correctedReference(), detailValue(record.recordText(), "Reference"));
        String type = firstNonBlank(record.correctedType(), detailValue(record.recordText(), "Type"));
        ImportPreviewRow row = previewRow(
                record.rowNumber(),
                List.of(date, description, amount, account, category, payment, reference, type),
                simpleRejectedHeaderIndex(),
                record.recordText()
        );
        if ("Possible duplicate".equalsIgnoreCase(firstProblem(record.problem())) && "Possible duplicate".equals(row.status())) {
            return row.withStatus("Ready", "");
        }
        return row;
    }

    private void viewImportDetails() {
        DataImportBatchRecord batch = selectedImportBatch();
        if (batch == null) {
            resultArea.setText("Select an import batch to view details.");
            return;
        }
        resultArea.setText(lines(
                "Import batch: " + safe(batch.batchKey()),
                "Original filename: " + safe(batch.originalFilename()),
                "File checksum: " + safe(batch.fileChecksum(), "-"),
                "Data type: " + safe(batch.dataType()),
                "Imported by: " + safe(batch.importedBy()),
                "Import date: " + safe(batch.createdAt()),
                "Total rows: " + batch.totalRows(),
                "Valid rows: " + batch.validRows(),
                "Duplicate rows: " + batch.duplicateRows(),
                "Rejected rows: " + batch.rejectedRows(),
                "Imported financial total: " + MoneyUtil.mwk(batch.importedTotal()),
                "Final status: " + safe(batch.status())
        ));
    }

    private void exportImportHistory() {
        try {
            Path exportFile = ExportPathService.writeTextExport(
                    ExportPathService.defaultFileName("Import History", "txt"),
                    importHistoryExportBody()
            );
            database.recordSystemLog("Data Intake", "Export Import History", "INFO", "Import history exported to " + exportFile);
            resultArea.setText(ExportPathService.successMessage(exportFile));
        } catch (IOException exception) {
            resultArea.setText(ExportPathService.failureMessage(exception) + "\n\nNo data was changed.");
            database.recordSystemLog("Data Intake", "Export Import History Failed", "ERROR", rootMessage(exception));
        }
    }

    private Optional<CorrectionValues> correctionDialog(RejectedImportRecord record) {
        Dialog<CorrectionValues> dialog = new Dialog<>();
        dialog.setTitle("PFMIS");
        dialog.setHeaderText("Open and Fix");
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType save = new ButtonType("Save Correction", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(cancel, save);

        TextField date = textField("Date");
        date.setText(firstNonBlank(record.correctedDate(), detailValue(record.recordText(), "Date")));
        TextField description = textField("Description");
        description.setText(firstNonBlank(record.correctedDescription(), detailValue(record.recordText(), "Description")));
        TextField amount = textField("Amount");
        amount.setText(record.correctedAmount() == null
                ? detailValue(record.recordText(), "Amount")
                : String.valueOf(record.correctedAmount()));
        TextField account = textField("Account");
        account.setText(firstNonBlank(record.correctedAccount(), detailValue(record.recordText(), "Account")));
        TextField category = textField("Category");
        category.setText(firstNonBlank(record.correctedCategory(), detailValue(record.recordText(), "Category")));
        TextField paymentMethod = textField("Payment method");
        paymentMethod.setText(firstNonBlank(record.correctedPaymentMethod(), detailValue(record.recordText(), "Payment method")));
        TextField reference = textField("Reference");
        reference.setText(firstNonBlank(record.correctedReference(), detailValue(record.recordText(), "Reference")));
        ComboBox<String> type = combo(List.of("INCOME", "EXPENSE"), firstNonBlank(record.correctedType(), detailValue(record.recordText(), "Type"), "EXPENSE"));

        Label problem = valueLabel("Problem: " + safe(record.problem()));
        problem.setWrapText(true);
        VBox body = new VBox(10,
                problem,
                fields(
                        field("Date", date),
                        wideField("Description", description),
                        field("Amount", amount),
                        field("Account", account),
                        field("Category", category),
                        field("Payment method", paymentMethod),
                        field("Reference", reference),
                        field("Type", type)
                )
        );
        body.setPrefWidth(720);
        dialog.getDialogPane().setContent(body);
        dialog.setResultConverter(buttonType -> {
            if (buttonType != save) {
                return null;
            }
            return new CorrectionValues(
                    text(date),
                    text(description),
                    parseAmount(text(amount)),
                    text(account),
                    text(category),
                    text(paymentMethod),
                    text(reference),
                    selected(type)
            );
        });
        return dialog.showAndWait();
    }

    private String importHistoryExportBody() {
        StringBuilder builder = new StringBuilder("Import History").append(System.lineSeparator()).append(System.lineSeparator());
        if (currentRows.isEmpty()) {
            builder.append("No import history records were found for the selected filters.").append(System.lineSeparator());
        }
        for (IntakeRow row : currentRows) {
            if (row.source() instanceof DataImportBatchRecord batch) {
                builder.append(batch.createdAt())
                        .append(" | ").append(batch.batchKey())
                        .append(" | File: ").append(batch.originalFilename())
                        .append(" | Type: ").append(batch.dataType())
                        .append(" | Imported: ").append(batch.importedRows())
                        .append(" | Rejected: ").append(batch.rejectedRows())
                        .append(" | User: ").append(batch.importedBy())
                        .append(" | Status: ").append(batch.status())
                        .append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private List<ImportPreviewRow> readyRows() {
        return previewRows.stream()
                .filter(row -> "Ready".equals(row.status()))
                .toList();
    }

    private int countStatus(String status) {
        return (int) previewRows.stream()
                .filter(row -> status.equals(row.status()))
                .count();
    }

    private String statusForProblems(List<String> problems) {
        if (problems.isEmpty()) {
            return "Ready";
        }
        boolean onlyMissing = problems.stream().allMatch(problem -> problem.toLowerCase(Locale.ENGLISH).contains("missing"));
        return onlyMissing ? "Missing information" : "Invalid";
    }

    private boolean isPossibleDuplicate(LocalDate date, Account account, String type, double amount, String description) {
        if (date == null || account == null || type == null) {
            return false;
        }
        String cleanDescription = normalize(description);
        return recentTransactions.stream().anyMatch(transaction ->
                date.toString().equals(transaction.getTransactionDate())
                        && safe(transaction.getAccountName()).equalsIgnoreCase(account.getAccountName())
                        && safe(transaction.getTransactionType()).equalsIgnoreCase(type)
                        && Math.abs(transaction.getAmount() - amount) < 0.01
                        && normalize(transaction.getDescription()).equals(cleanDescription)
        );
    }

    private Account findAccount(String accountName) {
        String clean = normalize(accountName);
        return accounts.stream()
                .filter(account -> normalize(account.getAccountName()).equals(clean))
                .findFirst()
                .orElse(null);
    }

    private Category findCategory(String categoryName, String transactionType) {
        String clean = normalize(categoryName);
        return categories.stream()
                .filter(category -> normalize(category.getCategoryName()).equals(clean))
                .filter(category -> categoryMatchesType(category, transactionType))
                .findFirst()
                .orElse(null);
    }

    private boolean categoryMatchesType(Category category, String transactionType) {
        String categoryType = safe(category.getCategoryType()).toUpperCase(Locale.ENGLISH);
        return "BOTH".equals(categoryType) || categoryType.equals(safe(transactionType).toUpperCase(Locale.ENGLISH));
    }

    private String normalizeTransactionType(String typeText, double amount) {
        String clean = safe(typeText).trim().toUpperCase(Locale.ENGLISH).replace('-', '_').replace(' ', '_');
        if (clean.isBlank()) {
            return amount < 0 ? "EXPENSE" : "INCOME";
        }
        if (clean.contains("EXPENSE") || clean.contains("DEBIT") || clean.equals("DR") || clean.equals("OUT")) {
            return "EXPENSE";
        }
        if (clean.contains("INCOME") || clean.contains("CREDIT") || clean.equals("CR") || clean.equals("IN")) {
            return "INCOME";
        }
        if (clean.contains("TRANSFER")) {
            return "TRANSFER";
        }
        return null;
    }

    private Double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim()
                .toUpperCase(Locale.ENGLISH)
                .replace("MWK", "")
                .replace("K", "")
                .replace(",", "")
                .replace(" ", "");
        boolean parenthesized = clean.startsWith("(") && clean.endsWith(")");
        if (parenthesized) {
            clean = "-" + clean.substring(1, clean.length() - 1);
        }
        try {
            return Double.parseDouble(clean);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private LocalDate parseFlexibleDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim();
        List<DateTimeFormatter> formats = List.of(
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("M/d/yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("yyyy/M/d", Locale.ENGLISH)
        );
        for (DateTimeFormatter formatter : formats) {
            try {
                return LocalDate.parse(clean, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private LocalDate parseOptionalDate(String value) {
        return value == null || value.isBlank() ? null : parseFlexibleDate(value);
    }

    private LocalDate parseCreatedDate(String value) {
        if (value == null || value.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private List<String> parseCsvLine(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '"') {
                if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (character == ',' && !quoted) {
                values.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(character);
            }
        }
        values.add(current.toString().trim());
        return values;
    }

    private Map<String, Integer> headerIndex(List<String> headers) {
        Map<String, Integer> index = new HashMap<>();
        for (int column = 0; column < headers.size(); column++) {
            index.put(headerKey(headers.get(column)), column);
        }
        return index;
    }

    private Map<String, Integer> simpleRejectedHeaderIndex() {
        Map<String, Integer> index = new HashMap<>();
        index.put(headerKey("date"), 0);
        index.put(headerKey("description"), 1);
        index.put(headerKey("amount"), 2);
        index.put(headerKey("account"), 3);
        index.put(headerKey("category"), 4);
        index.put(headerKey("payment method"), 5);
        index.put(headerKey("reference"), 6);
        index.put(headerKey("type"), 7);
        return index;
    }

    private String value(List<String> values, Map<String, Integer> headers, String... names) {
        for (String name : names) {
            Integer index = headers.get(headerKey(name));
            if (index != null && index >= 0 && index < values.size()) {
                return safe(values.get(index)).trim();
            }
        }
        return "";
    }

    private String headerKey(String value) {
        return safe(value).toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9]", "");
    }

    private String stripBom(String value) {
        return value == null ? "" : value.replace("\uFEFF", "");
    }

    private RejectedImportRecord selectedRejectedRecord() {
        List<RejectedImportRecord> selected = selectedRejectedRecords();
        return selected.isEmpty() ? null : selected.getFirst();
    }

    private List<RejectedImportRecord> selectedRejectedRecords() {
        if (table == null) {
            return List.of();
        }
        List<RejectedImportRecord> selected = table.getItems().stream()
                .filter(IntakeRow::isSelected)
                .map(IntakeRow::source)
                .filter(RejectedImportRecord.class::isInstance)
                .map(RejectedImportRecord.class::cast)
                .toList();
        if (!selected.isEmpty()) {
            return selected;
        }
        IntakeRow row = table.getSelectionModel().getSelectedItem();
        if (row != null && row.source() instanceof RejectedImportRecord record) {
            return List.of(record);
        }
        return List.of();
    }

    private DataImportBatchRecord selectedImportBatch() {
        if (table == null) {
            return null;
        }
        IntakeRow selected = table.getSelectionModel().getSelectedItem();
        if (selected == null && !currentRows.isEmpty()) {
            selected = currentRows.getFirst();
        }
        return selected != null && selected.source() instanceof DataImportBatchRecord batch ? batch : null;
    }

    private String importStatus(int importedRows, int rejectedRows) {
        if (importedRows <= 0 && rejectedRows > 0) {
            return "Rejected";
        }
        if (rejectedRows > 0) {
            return "Imported With Rejections";
        }
        return "Imported";
    }

    private String checksumOrBlank(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read > 0) {
                        digest.update(buffer, 0, read);
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            return "";
        }
    }

    private String recordText(ImportPreviewRow row) {
        return lines(
                "Date: " + safe(row.dateText()),
                "Description: " + safe(row.description()),
                "Amount: " + row.amountText(),
                "Account: " + safe(row.accountName()),
                "Category: " + safe(row.categoryName()),
                "Payment method: " + safe(row.paymentMethod()),
                "Reference: " + safe(row.reference()),
                "Type: " + safe(row.transactionType()),
                "Source row: " + safe(row.rawLine())
        );
    }

    private String detailValue(String details, String label) {
        if (details == null || details.isBlank()) {
            return "";
        }
        String prefix = label + ":";
        for (String line : details.split("\\R")) {
            if (line.regionMatches(true, 0, prefix, 0, prefix.length())) {
                return line.substring(prefix.length()).trim();
            }
        }
        return "";
    }

    private String recordSummary(RejectedImportRecord record) {
        String description = firstNonBlank(record.correctedDescription(), detailValue(record.recordText(), "Description"));
        return description.isBlank() ? "Row " + record.rowNumber() : description;
    }

    private String firstProblem(String problem) {
        if (problem == null || problem.isBlank()) {
            return "";
        }
        int separator = problem.indexOf(';');
        return separator >= 0 ? problem.substring(0, separator).trim() : problem.trim();
    }

    private boolean mentionsCalculatedTotal(String value) {
        String lower = safe(value).toLowerCase(Locale.ENGLISH);
        return CALCULATED_TOTALS.stream().anyMatch(lower::contains);
    }

    private boolean confirm(String title, String message, String confirmText) {
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType confirm = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, cancel, confirm);
        alert.setTitle("PFMIS");
        alert.setHeaderText(title);
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }

    private TableView<IntakeRow> table(String emptyMessage, boolean selectable, List<ColumnSpec> columns) {
        TableView<IntakeRow> view = new TableView<>();
        view.setPlaceholder(new Label(emptyMessage));
        view.setPrefHeight(360);
        view.setEditable(selectable);
        if (selectable) {
            TableColumn<IntakeRow, Boolean> selectColumn = new TableColumn<>("Select");
            selectColumn.setPrefWidth(70);
            selectColumn.setCellValueFactory(cell -> cell.getValue().selectedProperty());
            selectColumn.setCellFactory(CheckBoxTableCell.forTableColumn(selectColumn));
            view.getColumns().add(selectColumn);
        }
        for (int index = 0; index < columns.size(); index++) {
            ColumnSpec spec = columns.get(index);
            int valueIndex = index;
            TableColumn<IntakeRow, String> column = new TableColumn<>(spec.title());
            column.setPrefWidth(spec.width());
            column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().value(valueIndex)));
            view.getColumns().add(column);
        }
        TableActions.configureScrollableTable(view);
        return view;
    }

    private FlowPane fields(Node... nodes) {
        FlowPane pane = new FlowPane(10, 10, nodes);
        pane.setPrefWrapLength(1120);
        return pane;
    }

    private VBox field(String label, Node node) {
        VBox box = new VBox(5);
        box.getStyleClass().add("maintenance-simple-field");
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        box.getChildren().addAll(fieldLabel, node);
        return box;
    }

    private VBox wideField(String label, Node node) {
        VBox box = field(label, node);
        box.getStyleClass().setAll("maintenance-simple-field-wide");
        return box;
    }

    private VBox labelledBlock(String label, Node node) {
        VBox box = new VBox(8);
        box.getStyleClass().add("maintenance-simple-block");
        Label fieldLabel = new Label(label);
        fieldLabel.getStyleClass().add("field-label");
        box.getChildren().addAll(fieldLabel, node);
        return box;
    }

    private Node searchButton(String text) {
        Button button = new Button(text);
        button.setMinWidth(120);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> search());
        VBox box = new VBox(5);
        box.getStyleClass().add("maintenance-simple-field");
        Label label = new Label(" ");
        label.getStyleClass().add("field-label");
        box.getChildren().addAll(label, button);
        return box;
    }

    private ComboBox<String> combo(List<String> values, String selected) {
        ComboBox<String> comboBox = new ComboBox<>();
        comboBox.getItems().setAll(values);
        comboBox.setValue(values.contains(selected) ? selected : values.isEmpty() ? null : values.getFirst());
        comboBox.setMaxWidth(Double.MAX_VALUE);
        comboBox.getStyleClass().add("maintenance-input");
        return comboBox;
    }

    private TextField textField(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.getStyleClass().add("maintenance-input");
        field.setOnAction(event -> search());
        return field;
    }

    private TextArea textArea(String prompt, int rows) {
        TextArea area = new TextArea();
        area.setPromptText(prompt);
        area.setWrapText(true);
        area.setPrefRowCount(rows);
        area.getStyleClass().add("maintenance-input");
        return area;
    }

    private Label valueLabel(String value) {
        Label label = new Label(value);
        label.setWrapText(true);
        label.getStyleClass().add("settings-status-text");
        return label;
    }

    private GridPane summaryGrid(List<SummaryRow> rows) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("maintenance-simple-block");
        grid.setHgap(14);
        grid.setVgap(8);
        for (int index = 0; index < rows.size(); index++) {
            SummaryRow row = rows.get(index);
            Label name = new Label(row.name());
            name.getStyleClass().add("field-label");
            grid.add(name, 0, index);
            grid.add(row.value(), 1, index);
        }
        return grid;
    }

    private SummaryRow summary(String name, Label value) {
        return new SummaryRow(name, value);
    }

    private ColumnSpec column(String title, double width) {
        return new ColumnSpec(title, width);
    }

    private Window ownerWindow() {
        return contentContainer == null || contentContainer.getScene() == null ? null : contentContainer.getScene().getWindow();
    }

    private boolean isAll(String value) {
        return value == null || value.isBlank() || value.toLowerCase(Locale.ENGLISH).startsWith("all ");
    }

    private String selected(ComboBox<String> comboBox) {
        return comboBox == null || comboBox.getValue() == null ? "" : comboBox.getValue();
    }

    private String text(TextField field) {
        return field == null || field.getText() == null ? "" : field.getText().trim();
    }

    private void clear(TextField field) {
        if (field != null) {
            field.clear();
        }
    }

    private String normalize(String value) {
        return safe(value).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ENGLISH);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String lines(String... values) {
        return String.join(System.lineSeparator(), values);
    }

    private String userSafeMessage(Throwable throwable) {
        String message = rootMessage(throwable);
        return message == null || message.isBlank() ? "The selected data could not be processed." : message;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private String workspaceText() {
        try {
            SystemUser user = UserSession.getWorkspaceUser();
            return user.getDisplayName() + " (" + user.getUsername() + ")";
        } catch (RuntimeException exception) {
            return "No active workspace";
        }
    }

    private record ColumnSpec(String title, double width) {
    }

    private record SummaryRow(String name, Label value) {
    }

    private record CorrectionValues(
            String date,
            String description,
            Double amount,
            String account,
            String category,
            String paymentMethod,
            String reference,
            String type
    ) {
    }

    private record ImportPreviewRow(
            int rowNumber,
            String dateText,
            String description,
            String originalAmountText,
            String accountName,
            String categoryName,
            String paymentMethod,
            String reference,
            String originalTypeText,
            LocalDate date,
            Double amount,
            String transactionType,
            Integer accountId,
            Integer categoryId,
            String status,
            String problem,
            String rawLine
    ) {
        String amountText() {
            return amount == null ? safeAmount(originalAmountText) : MoneyUtil.mwk(amount);
        }

        String originalValue() {
            return rawLine == null || rawLine.isBlank() ? amountText() : rawLine;
        }

        ImportPreviewRow withStatus(String status, String problem) {
            return new ImportPreviewRow(
                    rowNumber,
                    dateText,
                    description,
                    originalAmountText,
                    accountName,
                    categoryName,
                    paymentMethod,
                    reference,
                    originalTypeText,
                    date,
                    amount,
                    transactionType,
                    accountId,
                    categoryId,
                    status,
                    problem,
                    rawLine
            );
        }

        private static String safeAmount(String value) {
            return value == null || value.isBlank() ? "-" : value;
        }
    }

    private static final class IntakeRow {
        private final List<String> values;
        private final Object source;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        private IntakeRow(List<String> values, Object source) {
            this.values = new ArrayList<>(values);
            this.source = source;
        }

        private String value(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : "";
        }

        private Object source() {
            return source;
        }

        private BooleanProperty selectedProperty() {
            return selected;
        }

        private boolean isSelected() {
            return selected.get();
        }
    }
}
