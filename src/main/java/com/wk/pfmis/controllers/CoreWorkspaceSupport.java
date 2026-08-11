package com.wk.pfmis.controllers;

import com.wk.pfmis.models.Account;
import com.wk.pfmis.models.Asset;
import com.wk.pfmis.models.Category;
import com.wk.pfmis.models.Goal;
import com.wk.pfmis.models.Project;
import com.wk.pfmis.utils.MoneyUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

final class CoreWorkspaceSupport {
    private CoreWorkspaceSupport() {
    }

    static <S> void bind(TableColumn<S, String> column, Function<S, String> valueFactory) {
        if (column != null) {
            column.setCellValueFactory(cell -> new SimpleStringProperty(safe(valueFactory.apply(cell.getValue()))));
        }
    }

    static <S> void setItems(TableView<S> table, List<S> rows, Label stateLabel, String emptyText) {
        if (table == null) {
            return;
        }
        List<S> safeRows = rows == null ? List.of() : rows;
        table.setItems(FXCollections.observableArrayList(safeRows));
        TableActions.configureScrollableTable(table);
        if (stateLabel != null) {
            stateLabel.setText(safeRows.isEmpty() ? emptyText : safeRows.size() + " record(s)");
        }
    }

    static String money(String currency, double amount) {
        return MoneyUtil.format(blank(currency, "MWK"), amount);
    }

    static String percent(double value) {
        return String.format(Locale.ENGLISH, "%.1f%%", value);
    }

    static double positiveAmount(TextInputControl field, String label) {
        double amount = amount(field, label);
        if (amount <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero.");
        }
        return amount;
    }

    static double amount(TextInputControl field, String label) {
        String raw = field == null ? "" : safe(field.getText());
        if (raw.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(raw.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + " must be a valid number.");
        }
    }

    static String required(TextInputControl field, String label) {
        String value = field == null ? "" : safe(field.getText());
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    static LocalDate requiredDate(DatePicker picker, String label) {
        LocalDate value = picker == null ? null : picker.getValue();
        if (value == null) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    static String date(DatePicker picker) {
        return picker == null || picker.getValue() == null ? "" : picker.getValue().toString();
    }

    static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    static String dash(String value) {
        return safe(value).isBlank() ? "-" : value.trim();
    }

    static String blank(String value, String fallback) {
        return safe(value).isBlank() ? fallback : value.trim();
    }

    static String selected(ComboBox<String> comboBox, String fallback) {
        return comboBox == null ? fallback : blank(comboBox.getValue(), fallback);
    }

    static Integer id(Account account) {
        return account == null ? null : account.getId();
    }

    static Integer id(Category category) {
        return category == null ? null : category.getId();
    }

    static Integer id(Project project) {
        return project == null ? null : project.getId();
    }

    static Account accountByName(List<Account> accounts, String name) {
        return accounts.stream()
                .filter(account -> Objects.equals(safe(account.getAccountName()), safe(name)))
                .findFirst()
                .orElse(null);
    }

    static Category categoryByName(List<Category> categories, String name) {
        return categories.stream()
                .filter(category -> Objects.equals(safe(category.getCategoryName()), safe(name)))
                .findFirst()
                .orElse(null);
    }

    static Project projectByName(List<Project> projects, String name) {
        return projects.stream()
                .filter(project -> Objects.equals(safe(project.getProjectName()), safe(name)))
                .findFirst()
                .orElse(null);
    }

    static Goal goalById(List<Goal> goals, int id) {
        return goals.stream().filter(goal -> goal.getId() == id).findFirst().orElse(null);
    }

    static Asset assetById(List<Asset> assets, int id) {
        return assets.stream().filter(asset -> asset.getId() == id).findFirst().orElse(null);
    }

    static void setComboItems(ComboBox<String> comboBox, String selected, String... values) {
        if (comboBox == null) {
            return;
        }
        comboBox.setItems(FXCollections.observableArrayList(values));
        comboBox.getSelectionModel().select(blank(selected, values.length == 0 ? "" : values[0]));
    }

    static void navigate(CoreWorkspaceRoute route) {
        OverviewScreenSupport.navigate(route);
    }
}
