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
        List<Category> items = new ArrayList<>();
        Category other = null;
        for (Category category : categories) {
            if ("Other".equalsIgnoreCase(category.getCategoryName())) {
                other = category;
            } else {
                items.add(category);
            }
        }
        items.add(other == null ? OTHER_CATEGORY : other);
        categoryBox.setItems(FXCollections.observableArrayList(items));
        if (selected != null && selected.getId() > 0) {
            selectById(categoryBox, selected.getId());
        } else if (!editorText.isEmpty()) {
            categoryBox.getEditor().setText(editorText);
        }
    }

    static void setItemsForType(ComboBox<Category> categoryBox, List<Category> categories, String categoryType) {
        String requestedType = clean(categoryType).toUpperCase();
        if (requestedType.isEmpty() || "BOTH".equals(requestedType)) {
            setItems(categoryBox, categories);
            return;
        }
        setItems(categoryBox, categories.stream()
                .filter(category -> categorySupportsType(category, requestedType))
                .toList());
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

    private static boolean categorySupportsType(Category category, String requestedType) {
        String categoryType = clean(category.getCategoryType()).toUpperCase();
        return "BOTH".equals(categoryType) || requestedType.equals(categoryType);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
