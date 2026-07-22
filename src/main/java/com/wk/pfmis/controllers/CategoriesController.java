package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Category;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;

public class CategoriesController {
    @FXML private Button saveCategoryButton;
    @FXML private TextField categoryNameField;
    @FXML private ComboBox<String> categoryTypeBox;
    @FXML private TableView<Category> categoriesTable;
    @FXML private TableColumn<Category, String> nameColumn;
    @FXML private TableColumn<Category, String> typeColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private Category editingCategory;

    @FXML
    public void initialize() {
        categoryTypeBox.setItems(FXCollections.observableArrayList("INCOME", "EXPENSE", "BOTH"));
        categoryTypeBox.getSelectionModel().select("EXPENSE");
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("categoryName"));
        typeColumn.setCellValueFactory(new PropertyValueFactory<>("categoryType"));
        categoriesTable.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                editSelected();
            }
        });
        configureContextMenu();
        refresh();
    }

    @FXML
    private void saveCategory() {
        try {
            String name = categoryNameField.getText().trim();
            if (name.isEmpty()) {
                UiAlerts.info("Enter a category name.");
                return;
            }
            if (editingCategory == null) {
                database.addCategory(name, categoryType());
            } else {
                database.updateCategory(editingCategory.getId(), name, categoryType());
            }
            clearForm();
            refresh();
            DataRefreshBus.notifyDataChanged();
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save category", exception);
        }
    }

    @FXML
    private void editSelected() {
        Category selected = categoriesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            UiAlerts.info("Select a category to edit.");
            return;
        }
        editingCategory = selected;
        categoryNameField.setText(selected.getCategoryName());
        categoryTypeBox.getSelectionModel().select(selected.getCategoryType());
        saveCategoryButton.setText("Save Changes");
    }

    @FXML
    private void clearForm() {
        editingCategory = null;
        categoryNameField.clear();
        categoryTypeBox.getSelectionModel().select("EXPENSE");
        saveCategoryButton.setText("Add Category");
        categoriesTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void refresh() {
        categoriesTable.setItems(FXCollections.observableArrayList(database.listCategories()));
    }

    private void configureContextMenu() {
        TableActions.installRowContextMenu(categoriesTable, this::categoryMenuItems);
    }

    private List<javafx.scene.control.MenuItem> categoryMenuItems(Category category) {
        return List.of(
                TableActions.menuItem("Edit Category", this::editSelected),
                TableActions.menuItem("Clear Category Form", this::clearForm),
                TableActions.separator(),
                TableActions.copyRowItem(categoriesTable, category),
                TableActions.exportTableItem(categoriesTable, "Categories"),
                TableActions.printTableItem(categoriesTable, "Categories"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private String categoryType() {
        String type = categoryTypeBox.getValue();
        return type == null || type.isBlank() ? "EXPENSE" : type;
    }
}
