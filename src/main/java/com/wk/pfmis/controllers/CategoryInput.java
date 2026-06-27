package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Category;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

final class CategoryInput {
    private static final Category OTHER_CATEGORY = new Category(-1, "Other", "BOTH");

    private CategoryInput() {
    }

    static void configure(ComboBox<Category> categoryBox) {
        categoryBox.setEditable(true);
        categoryBox.setPromptText("Select or type category");
        categoryBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Category category) {
                return category == null ? "" : category.getCategoryName();
            }

            @Override
            public Category fromString(String value) {
                String name = clean(value);
                if (name.isEmpty()) {
                    return null;
                }
                return categoryBox.getItems().stream()
                        .filter(category -> category.getCategoryName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(new Category(-1, name, "BOTH"));
            }
        });
    }

    static void setItems(ComboBox<Category> categoryBox, List<Category> categories) {
        String editorText = clean(categoryBox.getEditor().getText());
        Category selected = categoryBox.getValue();
        List<Category> items = new ArrayList<>(categories);
        boolean hasOther = items.stream()
                .anyMatch(category -> "Other".equalsIgnoreCase(category.getCategoryName()));
        if (!hasOther) {
            items.add(OTHER_CATEGORY);
        }
        categoryBox.setItems(FXCollections.observableArrayList(items));
        if (selected != null && selected.getId() > 0) {
            selectById(categoryBox, selected.getId());
        } else if (!editorText.isEmpty()) {
            categoryBox.getEditor().setText(editorText);
        }
    }

    static Integer resolveCategoryId(DatabaseHandler database, ComboBox<Category> categoryBox, String categoryType) {
        String typedName = clean(categoryBox.getEditor().getText());
        Category selected = categoryBox.getValue();
        String categoryName = !typedName.isEmpty()
                ? typedName
                : selected == null ? "" : clean(selected.getCategoryName());
        if (categoryName.isEmpty()) {
            return null;
        }
        return database.findOrCreateCategory(categoryName, categoryType).getId();
    }

    static void selectByName(ComboBox<Category> categoryBox, String categoryName) {
        String name = clean(categoryName);
        if (name.isEmpty()) {
            categoryBox.setValue(null);
            categoryBox.getEditor().clear();
            return;
        }
        categoryBox.getItems().stream()
                .filter(category -> category.getCategoryName().equalsIgnoreCase(name))
                .findFirst()
                .ifPresentOrElse(categoryBox::setValue, () -> categoryBox.getEditor().setText(name));
    }

    private static void selectById(ComboBox<Category> categoryBox, int categoryId) {
        categoryBox.getItems().stream()
                .filter(category -> category.getId() == categoryId)
                .findFirst()
                .ifPresent(categoryBox::setValue);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
