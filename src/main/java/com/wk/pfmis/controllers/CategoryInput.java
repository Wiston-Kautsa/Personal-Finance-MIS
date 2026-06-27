package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.Category;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

final class CategoryInput {
    private static final Category OTHER_CATEGORY = new Category(-1, "Other", "BOTH");
    private static final Map<ComboBox<Category>, List<Category>> CATEGORY_OPTIONS = new WeakHashMap<>();
    private static final Set<ComboBox<Category>> CONFIGURED_BOXES = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ComboBox<Category>> UPDATING_BOXES = Collections.newSetFromMap(new WeakHashMap<>());

    private CategoryInput() {
    }

    static void configure(ComboBox<Category> categoryBox) {
        categoryBox.setEditable(true);
        categoryBox.setPromptText("Select or type category");
        if (CONFIGURED_BOXES.add(categoryBox)) {
            categoryBox.getEditor().textProperty().addListener((observable, oldValue, newValue) -> {
                if (UPDATING_BOXES.contains(categoryBox)) {
                    return;
                }
                filterItems(categoryBox, clean(newValue));
            });
        }
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
        items = withOtherLast(items, other);
        CATEGORY_OPTIONS.put(categoryBox, items);
        setVisibleItems(categoryBox, items);
        if (selected != null && selected.getId() > 0) {
            selectById(categoryBox, selected.getId());
        } else if (!editorText.isEmpty()) {
            setEditorText(categoryBox, editorText);
            filterItems(categoryBox, editorText);
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
        UPDATING_BOXES.add(categoryBox);
        try {
            categoryBox.getItems().stream()
                    .filter(category -> category.getId() == categoryId)
                    .findFirst()
                    .ifPresent(categoryBox::setValue);
        } finally {
            UPDATING_BOXES.remove(categoryBox);
        }
    }

    private static void filterItems(ComboBox<Category> categoryBox, String query) {
        List<Category> sourceItems = CATEGORY_OPTIONS.getOrDefault(categoryBox, List.of());
        if (sourceItems.isEmpty()) {
            return;
        }
        String normalizedQuery = query.toLowerCase();
        List<Category> filtered = sourceItems.stream()
                .filter(category -> normalizedQuery.isEmpty()
                        || "Other".equalsIgnoreCase(category.getCategoryName())
                        || category.getCategoryName().toLowerCase().contains(normalizedQuery))
                .toList();
        setVisibleItems(categoryBox, filtered.isEmpty() ? sourceItems : filtered);
        if (!normalizedQuery.isEmpty() && categoryBox.isFocused()) {
            Platform.runLater(categoryBox::show);
        }
    }

    private static List<Category> withOtherLast(List<Category> categories, Category other) {
        List<Category> items = new ArrayList<>(categories);
        items.add(other == null ? OTHER_CATEGORY : other);
        return items;
    }

    private static void setVisibleItems(ComboBox<Category> categoryBox, List<Category> items) {
        UPDATING_BOXES.add(categoryBox);
        try {
            categoryBox.setItems(FXCollections.observableArrayList(items));
        } finally {
            UPDATING_BOXES.remove(categoryBox);
        }
    }

    private static void setEditorText(ComboBox<Category> categoryBox, String text) {
        UPDATING_BOXES.add(categoryBox);
        try {
            categoryBox.getEditor().setText(text);
        } finally {
            UPDATING_BOXES.remove(categoryBox);
        }
    }

    private static boolean categorySupportsType(Category category, String requestedType) {
        String categoryType = clean(category.getCategoryType()).toUpperCase();
        return "BOTH".equals(categoryType) || requestedType.equals(categoryType);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
