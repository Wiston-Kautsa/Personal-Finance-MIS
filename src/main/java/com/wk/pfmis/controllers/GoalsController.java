package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Goal;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class GoalsController {
    @FXML private TextField goalNameField;
    @FXML private TextField targetAmountField;
    @FXML private TextField currentAmountField;
    @FXML private TextField monthlyContributionField;
    @FXML private DatePicker targetDatePicker;
    @FXML private TableView<Goal> goalsTable;
    @FXML private TableColumn<Goal, String> nameColumn;
    @FXML private TableColumn<Goal, Double> targetColumn;
    @FXML private TableColumn<Goal, Double> currentColumn;
    @FXML private TableColumn<Goal, Double> remainingColumn;
    @FXML private TableColumn<Goal, Double> monthsColumn;
    @FXML private TableColumn<Goal, String> statusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("goalName"));
        targetColumn.setCellValueFactory(new PropertyValueFactory<>("targetAmount"));
        currentColumn.setCellValueFactory(new PropertyValueFactory<>("currentAmount"));
        remainingColumn.setCellValueFactory(new PropertyValueFactory<>("remainingAmount"));
        monthsColumn.setCellValueFactory(new PropertyValueFactory<>("monthsNeeded"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
        refresh();
    }

    @FXML
    private void addGoal() {
        try {
            String name = goalNameField.getText().trim();
            if (name.isEmpty()) {
                UiAlerts.info("Enter a goal name.");
                return;
            }
            database.addGoal(
                    name,
                    parseAmount(targetAmountField.getText()),
                    parseAmount(currentAmountField.getText()),
                    parseAmount(monthlyContributionField.getText()),
                    targetDatePicker.getValue() == null ? null : targetDatePicker.getValue().toString()
            );
            goalNameField.clear();
            targetAmountField.clear();
            currentAmountField.clear();
            monthlyContributionField.clear();
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to add goal", exception);
        }
    }

    @FXML
    private void refresh() {
        goalsTable.setItems(FXCollections.observableArrayList(database.listGoals()));
    }

    private double parseAmount(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        return Double.parseDouble(value.replace(",", "").trim());
    }
}
