package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.FinanceTransaction;
import com.wk.pfmis.models.Person;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TransactionsController {
    private static final String DEFAULT_LOAN_CATEGORY_NAME = "Personal Loan";
    private static final String LEGACY_LOAN_CATEGORY_NAME = "Loans";
    private static final List<String> LOAN_CATEGORY_NAMES = List.of(
            "Personal Loan",
            "Business Loan",
            "Emergency Loan",
            "Education Loan",
            "Medical Loan",
            "Family or Friend Loan",
            "Institutional Loan",
            "Salary Advance",
            "Asset or Equipment Loan",
            "Other Loan"
    );

    @FXML private Label headingLabel;
    @FXML private Label accountLabel;
    @FXML private Label amountLabel;
    @FXML private Label categoryLabel;
    @FXML private Label purposeLabel;
    @FXML private Label statusLabel;
    @FXML private Label dateLabel;
    @FXML private Label projectLabel;
    @FXML private Label personLabel;
    @FXML private Label activityLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label recordsLabel;
    @FXML private Button saveButton;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<ProjectActivity> activityBox;
    @FXML private ComboBox<Person> personBox;
    @FXML private ComboBox<String> typeBox;
    @FXML private ComboBox<String> purposeBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextField amountField;
    @FXML private DatePicker datePicker;
    @FXML private TextArea descriptionArea;
    @FXML private TableView<FinanceTransaction> transactionsTable;
    @FXML private TableColumn<FinanceTransaction, String> dateColumn;
    @FXML private TableColumn<FinanceTransaction, String> typeColumn;
    @FXML private TableColumn<FinanceTransaction, String> purposeColumn;
    @FXML private TableColumn<FinanceTransaction, String> personColumn;
    @FXML private TableColumn<FinanceTransaction, String> accountColumn;
    @FXML private TableColumn<FinanceTransaction, String> categoryColumn;
    @FXML private TableColumn<FinanceTransaction, String> projectColumn;
    @FXML private TableColumn<FinanceTransaction, String> activityColumn;
    @FXML private TableColumn<FinanceTransaction, String> statusColumn;
    @FXML private TableColumn<FinanceTransaction, Double> amountColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private FinanceTransaction editingTransaction;
    private String requestedTransactionType;
    private String requestedTransactionPurpose;
    private String requestedPersonName;

    @FXML
    public void initialize() {
        typeBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE", "TRANSFER"));
        purposeBox.setItems(FXCollections.observableArrayList(
                "NORMAL", "PROJECT_EXPENSE", "MONEY_LENT", "MONEY_BORROWED",
                "LENT_REPAID", "BORROWED_REPAID", "SUPPORT_GIVEN", "SAVINGS", "GOAL_CONTRIBUTION"));
        statusBox.setItems(FXCollections.observableArrayList("COMPLETED", "OPEN", "PARTIALLY_CLEARED", "CLEARED", "CANCELLED"));
        configureDisplayConverters();
        requestedTransactionType = NavigationBus.consumeRequestedTransactionType();
        requestedTransactionPurpose = NavigationBus.consumeRequestedTransactionPurpose();
        requestedPersonName = NavigationBus.consumeRequestedPersonName();
        typeBox.getSelectionModel().select(requestedTransactionType == null ? "EXPENSE" : requestedTransactionType);
        purposeBox.getSelectionModel().select(requestedTransactionPurpose == null ? "NORMAL" : requestedTransactionPurpose);
        statusBox.getSelectionModel().select(isNewLoanPurpose(requestedTransactionPurpose) ? "OPEN" : "COMPLETED");
        datePicker.setValue(LocalDate.now());
        configureTransactionLabels();
        configurePersonBox();

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("transactionDate"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("transactionType"));
        purposeColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(purposeDisplayName(cell.getValue().getTransactionPurpose())));
        personColumn.setCellValueFactory(new PropertyValueFactory<>("personName"));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        projectColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        activityColumn.setCellValueFactory(new PropertyValueFactory<>("projectActivityName"));
        statusColumn.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(statusDisplayName(cell.getValue().getTransactionStatus())));
        amountColumn.setCellValueFactory(new PropertyValueFactory<>("amount"));
        CategoryInput.configure(categoryBox);
        typeBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            refreshCategories();
            configureTransactionLabels();
        });
        purposeBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            refreshCategories();
            configureTransactionLabels();
        });
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> refreshActivitiesForProject());
        configureTransactionRowActions();
        refresh();
    }

    @FXML
    private void saveTransaction() {
        try {
            Account account = accountBox.getValue();
            if (account == null) {
                UiAlerts.info("Select an account.");
                return;
            }
            double amount = Double.parseDouble(amountField.getText().replace(",", "").trim());
            boolean loanPurpose = isLoanPurpose(purposeBox.getValue());
            Integer categoryId = resolveCategoryId(loanPurpose);
            Integer projectId = loanPurpose || projectBox.getValue() == null ? null : projectBox.getValue().getId();
            Integer activityId = loanPurpose || activityBox.getValue() == null ? null : activityBox.getValue().getId();
            Integer personId = resolvePersonId();
            if (requiresPerson() && personId == null) {
                UiAlerts.info("Enter or select a person for this transaction.");
                return;
            }
            if (editingTransaction == null) {
                database.recordTransaction(
                        account.getId(),
                        categoryId,
                        projectId,
                        activityId,
                        personId,
                        typeBox.getValue(),
                        purposeBox.getValue(),
                        statusBox.getValue(),
                        amount,
                        datePicker.getValue(),
                        descriptionArea.getText().trim(),
                        null,
                        null
                );
            } else {
                database.updateTransaction(
                        editingTransaction.getId(),
                        account.getId(),
                        categoryId,
                        projectId,
                        activityId,
                        personId,
                        typeBox.getValue(),
                        purposeBox.getValue(),
                        statusBox.getValue(),
                        amount,
                        datePicker.getValue(),
                        descriptionArea.getText().trim(),
                        editingTransaction.getPaymentMethod(),
                        editingTransaction.getReferenceNumber()
                );
                editingTransaction = null;
            }
            amountField.clear();
            descriptionArea.clear();
            activityBox.setValue(null);
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Transaction saved and account balance updated.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save transaction", exception);
        }
    }

    @FXML
    private void refresh() {
        Project selectedProject = projectBox.getValue();
        ProjectActivity selectedActivity = activityBox.getValue();
        Person selectedPerson = personBox.getValue();
        String typedPersonName = personNameText();
        accountBox.setItems(FXCollections.observableArrayList(database.listAccounts()));
        refreshCategories();
        projectBox.setItems(FXCollections.observableArrayList(database.listProjects()));
        selectProjectById(selectedProject == null ? null : selectedProject.getId());
        refreshActivitiesForProject();
        selectActivityById(selectedActivity == null ? null : selectedActivity.getId());
        personBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        if (requestedPersonName != null && !requestedPersonName.isBlank()) {
            selectPersonByName(requestedPersonName);
            requestedPersonName = null;
        } else if (selectedPerson != null) {
            selectPersonByName(selectedPerson.getFullName());
        } else if (!typedPersonName.isBlank()) {
            personBox.getEditor().setText(typedPersonName);
        }
        List<FinanceTransaction> transactions = database.listRecentTransactions(100);
        if (requestedTransactionType != null) {
            transactions = transactions.stream()
                    .filter(transaction -> requestedTransactionType.equals(transaction.getTransactionType()))
                    .toList();
        }
        if (requestedTransactionPurpose != null) {
            transactions = transactions.stream()
                    .filter(transaction -> requestedTransactionPurpose.equals(transaction.getTransactionPurpose()))
                    .toList();
        }
        transactionsTable.setItems(FXCollections.observableArrayList(transactions));
    }

    private void refreshCategories() {
        if (isLoanPurpose(purposeBox.getValue())) {
            Category selectedCategory = categoryBox.getValue();
            List<Category> loanCategories = loanCategories();
            categoryBox.setDisable(false);
            categoryBox.setEditable(false);
            categoryBox.setItems(FXCollections.observableArrayList(loanCategories));
            selectLoanCategory(selectedCategory, loanCategories);
            categoryBox.setPromptText("Select loan category");
            return;
        }

        categoryBox.setDisable(false);
        categoryBox.setEditable(true);
        categoryBox.setPromptText("Select, type, or use Other");
        categoryBox.getEditor().setPromptText("Select, type, or use Other");
        CategoryInput.setItemsForType(categoryBox, database.listCategories(), transactionCategoryType());
    }

    private Integer resolveCategoryId(boolean loanPurpose) {
        if (loanPurpose) {
            return resolveLoanCategoryId();
        }
        return CategoryInput.resolveCategoryId(database, categoryBox, transactionCategoryType());
    }

    private Integer resolveLoanCategoryId() {
        Category selectedCategory = categoryBox.getValue();
        String categoryName = selectedCategory == null ? "" : selectedCategory.getCategoryName();
        if (categoryName == null || categoryName.isBlank()) {
            categoryName = DEFAULT_LOAN_CATEGORY_NAME;
        }
        return database.findOrCreateCategory(categoryName, transactionCategoryType()).getId();
    }

    private List<Category> loanCategories() {
        List<Category> existingCategories = database.listCategories();
        Map<String, Category> categoriesByName = new LinkedHashMap<>();
        int syntheticId = -1;
        for (String categoryName : LOAN_CATEGORY_NAMES) {
            Category existingCategory = findCategoryByName(existingCategories, categoryName);
            categoriesByName.put(
                    categoryName.toLowerCase(),
                    existingCategory == null ? new Category(syntheticId--, categoryName, transactionCategoryType()) : existingCategory
            );
        }
        for (Category category : existingCategories) {
            String categoryName = category.getCategoryName();
            if (isLoanCategoryName(categoryName)) {
                categoriesByName.putIfAbsent(categoryName.trim().toLowerCase(), category);
            }
        }
        return new ArrayList<>(categoriesByName.values());
    }

    private Category findCategoryByName(List<Category> categories, String categoryName) {
        return categories.stream()
                .filter(category -> category.getCategoryName().equalsIgnoreCase(categoryName))
                .findFirst()
                .orElse(null);
    }

    private void selectLoanCategory(Category selectedCategory, List<Category> loanCategories) {
        String selectedName = selectedCategory == null ? "" : selectedCategory.getCategoryName();
        String preferredName = selectedName == null || selectedName.isBlank()
                || (LEGACY_LOAN_CATEGORY_NAME.equalsIgnoreCase(selectedName) && editingTransaction == null)
                ? DEFAULT_LOAN_CATEGORY_NAME
                : selectedName;
        loanCategories.stream()
                .filter(category -> category.getCategoryName().equalsIgnoreCase(preferredName))
                .findFirst()
                .or(() -> loanCategories.stream().findFirst())
                .ifPresent(categoryBox::setValue);
    }

    private boolean isLoanCategoryName(String categoryName) {
        if (categoryName == null || categoryName.isBlank()) {
            return false;
        }
        String normalizedName = categoryName.trim().toLowerCase();
        return LEGACY_LOAN_CATEGORY_NAME.equalsIgnoreCase(categoryName)
                || normalizedName.contains("loan")
                || normalizedName.contains("advance");
    }

    @FXML
    private void editSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to edit.");
            return;
        }
        editingTransaction = selected;
        selectAccountByName(selected.getAccountName());
        selectCategoryByName(selected.getCategoryName());
        selectProjectByName(selected.getProjectName());
        selectActivityById(selected.getProjectActivityId());
        selectPersonByName(selected.getPersonName());
        typeBox.getSelectionModel().select(selected.getTransactionType());
        purposeBox.getSelectionModel().select(selected.getTransactionPurpose());
        statusBox.getSelectionModel().select(selected.getTransactionStatus());
        amountField.setText(String.valueOf(selected.getAmount()));
        if (selected.getTransactionDate() != null && !selected.getTransactionDate().isBlank()) {
            datePicker.setValue(LocalDate.parse(selected.getTransactionDate()));
        }
        descriptionArea.setText(selected.getDescription() == null ? "" : selected.getDescription());
    }

    @FXML
    private void viewSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to view.");
            return;
        }
        UiAlerts.info(
                "Date: " + selected.getTransactionDate()
                        + "\nType: " + selected.getTransactionType()
                        + "\nPurpose: " + selected.getTransactionPurpose()
                        + "\nAccount: " + selected.getAccountName()
                        + "\nCategory: " + blankToDash(selected.getCategoryName())
                        + "\nProject: " + blankToDash(selected.getProjectName())
                        + "\nActivity: " + blankToDash(selected.getProjectActivityName())
                        + "\nStatus: " + selected.getTransactionStatus()
                        + "\nAmount: " + MoneyUtil.mwk(selected.getAmount())
                        + "\nDescription: " + blankToDash(selected.getDescription())
        );
    }

    @FXML
    private void deleteSelected() {
        FinanceTransaction selected = transactionsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a transaction to delete.");
            return;
        }
        try {
            database.deleteTransaction(selected.getId());
            if (editingTransaction != null && editingTransaction.getId() == selected.getId()) {
                editingTransaction = null;
                amountField.clear();
                descriptionArea.clear();
                activityBox.setValue(null);
            }
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to delete transaction", exception);
        }
    }

    @FXML
    private void exportExcel() {
        TableActions.exportVisibleTableToCsv(transactionsTable, recordsLabel.getText());
    }

    @FXML
    private void printTransactions() {
        TableActions.printTable(transactionsTable, recordsLabel.getText());
    }

    private void configureTransactionRowActions() {
        TableActions.installRowContextMenu(transactionsTable, this::transactionMenuItems);
    }

    private List<javafx.scene.control.MenuItem> transactionMenuItems(FinanceTransaction transaction) {
        return List.of(
                TableActions.menuItem("View Transaction", this::viewSelected),
                TableActions.menuItem("Edit Transaction", this::editSelected),
                TableActions.menuItem("Void Transaction", this::deleteSelected),
                TableActions.separator(),
                TableActions.copyRowItem(transactionsTable, transaction),
                TableActions.exportTableItem(transactionsTable, recordsLabel.getText()),
                TableActions.printTableItem(transactionsTable, recordsLabel.getText()),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void selectAccountByName(String accountName) {
        accountBox.getItems().stream()
                .filter(account -> account.getAccountName().equals(accountName))
                .findFirst()
                .ifPresent(accountBox::setValue);
    }

    private void selectCategoryByName(String categoryName) {
        CategoryInput.selectByName(categoryBox, categoryName);
    }

    private void selectProjectByName(String projectName) {
        projectBox.setValue(null);
        projectBox.getItems().stream()
                .filter(project -> projectName != null && project.getProjectName().equals(projectName))
                .findFirst()
                .ifPresent(projectBox::setValue);
    }

    private void selectProjectById(Integer projectId) {
        projectBox.setValue(null);
        if (projectId == null) {
            return;
        }
        projectBox.getItems().stream()
                .filter(project -> project.getId() == projectId)
                .findFirst()
                .ifPresent(projectBox::setValue);
    }

    private void selectActivityById(Integer activityId) {
        activityBox.setValue(null);
        if (activityId == null) {
            return;
        }
        activityBox.getItems().stream()
                .filter(activity -> activity.getId() == activityId)
                .findFirst()
                .ifPresent(activityBox::setValue);
    }

    private void refreshActivitiesForProject() {
        Project project = projectBox.getValue();
        if (project == null) {
            activityBox.setItems(FXCollections.observableArrayList());
            activityBox.setDisable(true);
            activityBox.setPromptText("Select project first");
            return;
        }

        ProjectActivity selectedActivity = activityBox.getValue();
        List<ProjectActivity> activities = database.listProjectActivities().stream()
                .filter(activity -> activity.getProjectId() == project.getId())
                .filter(this::isUnfinishedActivity)
                .toList();
        activityBox.setItems(FXCollections.observableArrayList(activities));
        activityBox.setDisable(activities.isEmpty());
        activityBox.setPromptText(activities.isEmpty() ? "No open activities" : "Select activity");
        if (selectedActivity != null && selectedActivity.getProjectId() == project.getId()) {
            selectActivityById(selectedActivity.getId());
        }
    }

    private boolean isUnfinishedActivity(ProjectActivity activity) {
        String status = activity.getStatus();
        if (status == null || status.isBlank()) {
            return true;
        }
        return switch (status.trim().toUpperCase()) {
            case "COMPLETED", "PAID", "CANCELLED", "CANCELED", "FINISHED", "DONE" -> false;
            default -> true;
        };
    }

    private void selectPersonByName(String personName) {
        if (personName == null || personName.isBlank()) {
            personBox.setValue(null);
            personBox.getEditor().clear();
            return;
        }
        personBox.setValue(null);
        personBox.getItems().stream()
                .filter(person -> personName != null && person.getFullName().equalsIgnoreCase(personName.trim()))
                .findFirst()
                .ifPresent(personBox::setValue);
        if (personBox.getValue() == null) {
            personBox.getEditor().setText(personName);
        }
    }

    private String transactionCategoryType() {
        return switch (typeBox.getValue()) {
            case "INCOME" -> "INCOME";
            case "EXPENSE" -> "EXPENSE";
            default -> "BOTH";
        };
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private void configureTransactionLabels() {
        String purpose = purposeBox.getValue();
        if ("MONEY_LENT".equals(purpose)) {
            configureLabels(
                    "Lend Money",
                    "Paid From Account",
                    "Amount Lent",
                    "Loan Category",
                    "Lend To",
                    "Person or institution you are lending to",
                    "Loan Terms / Notes",
                    "Save Loan",
                    "Loan Records",
                    true
            );
            return;
        }
        if ("MONEY_BORROWED".equals(purpose)) {
            configureLabels(
                    "Borrow Money",
                    "Receiving Account",
                    "Amount Borrowed",
                    "Loan Category",
                    "Borrow From",
                    "Person or institution you are borrowing from",
                    "Loan Terms / Notes",
                    "Save Borrowing",
                    "Loan Records",
                    true
            );
            return;
        }
        if ("LENT_REPAID".equals(purpose)) {
            configureLabels(
                    "Receive Repayment",
                    "Receiving Account",
                    "Repayment Received",
                    "Loan Category",
                    "Paid By",
                    "Person or institution paying you back",
                    "Repayment Notes",
                    "Save Repayment",
                    "Loan Records",
                    true
            );
            return;
        }
        if ("BORROWED_REPAID".equals(purpose)) {
            configureLabels(
                    "Repay Borrowed Money",
                    "Paid From Account",
                    "Repayment Amount",
                    "Loan Category",
                    "Pay To",
                    "Person or institution you are repaying",
                    "Repayment Notes",
                    "Save Repayment",
                    "Loan Records",
                    true
            );
            return;
        }
        if ("SUPPORT_GIVEN".equals(purpose)) {
            configureLabels(
                    "Give Support",
                    "Paid From Account",
                    "Support Amount",
                    "Expense Category",
                    "Recipient",
                    "Person or institution receiving support",
                    "Support Notes",
                    "Save Support",
                    "Support Records",
                    true
            );
            return;
        }
        if ("INCOME".equals(typeBox.getValue())) {
            configureLabels(
                    "Receive Money",
                    "Receiving Account",
                    "Amount",
                    "Income Category",
                    "Person / Institution",
                    "Optional person or institution",
                    "Description",
                    "Save Income",
                    "Income Records",
                    false
            );
            return;
        }
        configureLabels(
                "Record Expense",
                "Paid From Account",
                "Amount",
                "Expense Category",
                "Person / Institution",
                "Optional person or institution",
                "Description",
                "Save Expense",
                "Expense Records",
                false
        );
    }

    private void configureLabels(
            String heading,
            String account,
            String amount,
            String category,
            String person,
            String personPrompt,
            String description,
            String saveText,
            String records,
            boolean loanMode
    ) {
        headingLabel.setText(heading);
        accountLabel.setText(account);
        amountLabel.setText(amount);
        categoryLabel.setText(category);
        purposeLabel.setText(loanMode ? "Loan Activity" : "Purpose");
        statusLabel.setText(loanMode ? "Loan Status" : "Status");
        dateLabel.setText(loanMode ? "Loan Date" : "Date");
        personLabel.setText(person);
        descriptionLabel.setText(description);
        amountField.setPromptText(loanMode ? "0.00" : "30000");
        personBox.setPromptText(personPrompt);
        personBox.getEditor().setPromptText(personPrompt);
        saveButton.setText(saveText);
        recordsLabel.setText(records);
        setProjectActivityVisible(!loanMode);
        setPersonPlacement(loanMode);
        if (loanMode) {
            projectBox.setValue(null);
            activityBox.setValue(null);
        }
    }

    private void configurePersonBox() {
        personBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Person person) {
                return person == null ? "" : person.getFullName();
            }

            @Override
            public Person fromString(String value) {
                if (value == null || value.isBlank()) {
                    return null;
                }
                return personBox.getItems().stream()
                        .filter(person -> person.getFullName().equalsIgnoreCase(value.trim()))
                        .findFirst()
                        .orElse(null);
            }
        });
    }

    private void configureDisplayConverters() {
        purposeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return purposeDisplayName(value);
            }

            @Override
            public String fromString(String value) {
                return purposeBox.getItems().stream()
                        .filter(item -> item.equals(value) || purposeDisplayName(item).equalsIgnoreCase(value))
                        .findFirst()
                        .orElse(value);
            }
        });
        statusBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return statusDisplayName(value);
            }

            @Override
            public String fromString(String value) {
                return statusBox.getItems().stream()
                        .filter(item -> item.equals(value) || statusDisplayName(item).equalsIgnoreCase(value))
                        .findFirst()
                        .orElse(value);
            }
        });
    }

    private Integer resolvePersonId() {
        Person selectedPerson = personBox.getValue();
        if (selectedPerson != null) {
            return selectedPerson.getId();
        }

        String personName = personNameText();
        if (personName.isBlank()) {
            return null;
        }

        Person existingPerson = database.listPeople().stream()
                .filter(person -> person.getFullName().equalsIgnoreCase(personName))
                .findFirst()
                .orElse(null);
        if (existingPerson != null) {
            return existingPerson.getId();
        }

        database.addPerson(personName, "", "", "Auto-created from transaction entry");
        personBox.setItems(FXCollections.observableArrayList(database.listPeople()));
        Person createdPerson = database.listPeople().stream()
                .filter(person -> person.getFullName().equalsIgnoreCase(personName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Failed to create person"));
        personBox.setValue(createdPerson);
        return createdPerson.getId();
    }

    private boolean requiresPerson() {
        String purpose = purposeBox.getValue();
        return "MONEY_LENT".equals(purpose)
                || "SUPPORT_GIVEN".equals(purpose)
                || "LENT_REPAID".equals(purpose)
                || "MONEY_BORROWED".equals(purpose)
                || "BORROWED_REPAID".equals(purpose);
    }

    private boolean isLoanPurpose(String purpose) {
        return "MONEY_LENT".equals(purpose)
                || "MONEY_BORROWED".equals(purpose)
                || "LENT_REPAID".equals(purpose)
                || "BORROWED_REPAID".equals(purpose);
    }

    private boolean isNewLoanPurpose(String purpose) {
        return "MONEY_LENT".equals(purpose) || "MONEY_BORROWED".equals(purpose);
    }

    private void setProjectActivityVisible(boolean visible) {
        projectLabel.setVisible(visible);
        projectLabel.setManaged(visible);
        projectBox.setVisible(visible);
        projectBox.setManaged(visible);
        activityLabel.setVisible(visible);
        activityLabel.setManaged(visible);
        activityBox.setVisible(visible);
        activityBox.setManaged(visible);
    }

    private void setPersonPlacement(boolean loanMode) {
        GridPane.setColumnIndex(personLabel, loanMode ? 0 : 2);
        GridPane.setColumnIndex(personBox, loanMode ? 1 : 3);
        GridPane.setColumnSpan(personBox, loanMode ? 3 : 1);
        personBox.setPrefWidth(loanMode ? 720 : 180);
    }

    private String purposeDisplayName(String purpose) {
        if (purpose == null) {
            return "";
        }
        return switch (purpose) {
            case "NORMAL" -> "Normal";
            case "PROJECT_EXPENSE" -> "Project Expense";
            case "MONEY_LENT" -> "Lend Money";
            case "MONEY_BORROWED" -> "Borrow Money";
            case "LENT_REPAID" -> "Receive Repayment";
            case "BORROWED_REPAID" -> "Repay Borrowed Money";
            case "SUPPORT_GIVEN" -> "Support Given";
            case "SAVINGS" -> "Savings";
            case "GOAL_CONTRIBUTION" -> "Goal Contribution";
            default -> purpose;
        };
    }

    private String statusDisplayName(String status) {
        if (status == null) {
            return "";
        }
        return switch (status) {
            case "COMPLETED" -> "Completed";
            case "OPEN" -> "Open";
            case "PARTIALLY_CLEARED" -> "Partially Cleared";
            case "CLEARED" -> "Cleared";
            case "CANCELLED" -> "Cancelled";
            default -> status;
        };
    }

    private String personNameText() {
        String text = personBox.getEditor().getText();
        if (text == null || text.isBlank()) {
            Person selectedPerson = personBox.getValue();
            text = selectedPerson == null ? "" : selectedPerson.getFullName();
        }
        return text == null ? "" : text.trim();
    }
}
