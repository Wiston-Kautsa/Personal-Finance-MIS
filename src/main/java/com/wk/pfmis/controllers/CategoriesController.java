package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Category;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

public class CategoriesController {
    @FXML private TextField categoryNameField;
    @FXML private ComboBox<String> categoryTypeBox;
    @FXML private TableView<Category> categoriesTable;
    @FXML private TableColumn<Category, String> nameColumn;
    @FXML private TableColumn<Category, String> typeColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        categoryTypeBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE", "BOTH"));
        categoryTypeBox.getSelectionModel().select("EXPENSE");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("categoryType"));
        refresh();
    }

    @FXML
    private void addCategory() {
        try {
            String name = categoryNameField.getText().trim();
            if (name.isEmpty()) {
                UiAlerts.info("Enter a category name.");
                return;
            }
            database.addCategory(name, categoryTypeBox.getValue());
            categoryNameField.clear();
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to add category", exception);
        }
    }

    @FXML
    private void refresh() {
        categoriesTable.setItems(FXCollections.observableArrayList(database.listCategories()));
    }
}
