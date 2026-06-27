package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.models.ProjectActivity;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;

public class ProjectActivitiesController {
    @FXML private ComboBox<Project> projectBox;
    @FXML private ComboBox<String> projectStatusBox;
    @FXML private TextField activityNameField;
    @FXML private DatePicker activityDatePicker;
    @FXML private TextField amountUsedField;
    @FXML private ComboBox<Category> categoryBox;
    @FXML private ComboBox<Account> accountBox;
    @FXML private ComboBox<String> paymentMethodBox;
    @FXML private ComboBox<String> statusBox;
    @FXML private TextArea descriptionArea;
    @FXML private TextArea reasonArea;
    @FXML private TableView<ProjectActivity> activitiesTable;
    @FXML private TableColumn<ProjectActivity, String> dateColumn;
    @FXML private TableColumn<ProjectActivity, String> projectColumn;
    @FXML private TableColumn<ProjectActivity, String> activityColumn;
    @FXML private TableColumn<ProjectActivity, String> categoryColumn;
    @FXML private TableColumn<ProjectActivity, String> amountUsedColumn;
    @FXML private TableColumn<ProjectActivity, String> accountColumn;
    @FXML private TableColumn<ProjectActivity, String> statusColumn;
    @FXML private TableColumn<ProjectActivity, String> reasonColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        activityDatePicker.setValue(LocalDate.now());
        projectStatusBox.setItems(FXCollections.observableArrayList("ACTIVE", "PLANNED", "COMPLETED", "ON HOLD", "CANCELLED"));
        statusBox.setItems(FXCollections.observableArrayList("Completed", "Paid", "Pending", "Cancelled"));
        statusBox.getSelectionModel().select("Completed");
        projectBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            projectStatusBox.getSelectionModel().select(newValue == null ? "ACTIVE" : newValue.getStatus());
        });

        dateColumn.setCellValueFactory(new PropertyValueFactory<>("activityDate"));
        projectColumn.setCellValueFactory(new PropertyValueFactory<>("projectName"));
        activityColumn.setCellValueFactory(new PropertyValueFactory<>("activityName"));
        categoryColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        amountUsedColumn.setCellValueFactory(cell -> new SimpleStringProperty(MoneyUtil.mwk(cell.getValue().getAmountUsed())));
        accountColumn.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        reasonColumn.setCellValueFactory(cell -> new SimpleStringProperty(reasonOrDescription(cell.getValue())));
        refresh();
    }

    @FXML
    private void saveActivity() {
        try {
            Project project = projectBox.getValue();
            Account account = accountBox.getValue();
            if (project == null) {
                UiAlerts.info("Select a project first.");
                return;
            }
            if (account == null) {
                UiAlerts.info("Select the account paid from.");
                return;
            }
            String activityName = activityNameField.getText() == null ? "" : activityNameField.getText().trim();
            if (activityName.isEmpty()) {
                UiAlerts.info("Enter an activity name.");
                return;
            }
            database.addProjectActivity(
                    project.getId(),
                    account.getId(),
                    categoryBox.getValue() == null ? null : categoryBox.getValue().getId(),
                    activityName,
                    textValue(descriptionArea),
                    amountUsedValue(),
                    activityDatePicker.getValue() == null ? LocalDate.now() : activityDatePicker.getValue(),
                    paymentMethodValue(),
                    textValue(reasonArea),
                    statusBox.getValue()
            );
            database.updateProjectStatus(project.getId(), projectStatusBox.getValue());
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
            UiAlerts.info("Activity saved and project expense recorded.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save activity", exception);
        }
    }

    @FXML
    private void clearForm() {
        activityNameField.clear();
        amountUsedField.clear();
        activityDatePicker.setValue(LocalDate.now());
        descriptionArea.clear();
        reasonArea.clear();
        paymentMethodBox.getSelectionModel().select("Cash");
        statusBox.getSelectionModel().select("Completed");
    }

    @FXML
    private void refresh() {
        Project selectedProject = projectBox.getValue();
        projectBox.setItems(FXCollections.observableArrayList(database.listProjects()));
        if (selectedProject != null) {
            projectBox.getItems().stream()
                    .filter(project -> project.getId() == selectedProject.getId())
                    .findFirst()
                    .ifPresent(project -> projectBox.getSelectionModel().select(project));
        } else if (!projectBox.getItems().isEmpty()) {
            projectBox.getSelectionModel().selectFirst();
        }
        if (projectBox.getValue() != null) {
            projectStatusBox.getSelectionModel().select(projectBox.getValue().getStatus());
        } else {
            projectStatusBox.getSelectionModel().select("ACTIVE");
        }

        accountBox.setItems(FXCollections.observableArrayList(database.listAccounts()));
        if (!accountBox.getItems().isEmpty() && accountBox.getValue() == null) {
            accountBox.getSelectionModel().selectFirst();
        }
        categoryBox.setItems(FXCollections.observableArrayList(
                database.listCategories().stream()
                        .filter(category -> "EXPENSE".equals(category.getCategoryType()) || "BOTH".equals(category.getCategoryType()))
                        .toList()
        ));
        paymentMethodBox.setItems(FXCollections.observableArrayList(database.listPaymentMethodSuggestions()));
        if (paymentMethodBox.getValue() == null) {
            paymentMethodBox.getSelectionModel().select("Cash");
        }
        activitiesTable.setItems(FXCollections.observableArrayList(database.listProjectActivities()));
    }

    private double amountUsedValue() {
        String value = amountUsedField.getText() == null ? "" : amountUsedField.getText().replace(",", "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter the amount used.");
        }
        return Double.parseDouble(value);
    }

    private String paymentMethodValue() {
        String value = paymentMethodBox.getEditor().getText();
        if (value == null || value.isBlank()) {
            value = paymentMethodBox.getValue();
        }
        return value == null ? "" : value.trim();
    }

    private String textValue(TextArea area) {
        return area.getText() == null ? "" : area.getText().trim();
    }

    private String reasonOrDescription(ProjectActivity activity) {
        if (activity.getReason() != null && !activity.getReason().isBlank()) {
            return activity.getReason();
        }
        return activity.getDescription();
    }
}
