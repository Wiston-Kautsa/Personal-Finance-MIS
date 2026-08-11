package com.wk.pfmis.controllers;

import com.wk.pfmis.services.OverviewWorkspaceService.OverviewRow;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

final class OverviewScreenSupport {
    private OverviewScreenSupport() {
    }

    @SafeVarargs
    static void configureTable(
            TableView<OverviewRow> table,
            TableColumn<OverviewRow, String> primary,
            TableColumn<OverviewRow, String> secondary,
            TableColumn<OverviewRow, String> tertiary,
            TableColumn<OverviewRow, String> amount,
            TableColumn<OverviewRow, String> date,
            TableColumn<OverviewRow, String> status,
            TableColumn<OverviewRow, String> action,
            TableColumn<OverviewRow, String>... optionalColumns
    ) {
        if (table == null) {
            return;
        }
        if (primary != null) {
            primary.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().primary()));
        }
        if (secondary != null) {
            secondary.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().secondary()));
        }
        if (tertiary != null) {
            tertiary.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().tertiary()));
        }
        if (amount != null) {
            amount.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().amount()));
        }
        if (date != null) {
            date.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().date()));
        }
        if (status != null) {
            status.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status()));
        }
        if (action != null) {
            action.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().action()));
        }
        for (TableColumn<OverviewRow, String> column : optionalColumns) {
            if (column != null) {
                column.setCellValueFactory(cell -> new SimpleStringProperty(""));
            }
        }
        TableActions.configureScrollableTable(table);
    }

    static void setRows(TableView<OverviewRow> table, List<OverviewRow> rows, Label stateLabel, String emptyText) {
        if (table == null) {
            return;
        }
        List<OverviewRow> safeRows = rows == null ? List.of() : rows;
        table.setItems(FXCollections.observableArrayList(safeRows));
        if (stateLabel != null) {
            stateLabel.setText(safeRows.isEmpty() ? emptyText : safeRows.size() + " item(s)");
        }
    }

    static void setEmptyState(Label label, boolean empty, String emptyText, String readyText) {
        if (label != null) {
            label.setText(empty ? emptyText : readyText);
        }
    }

    static void navigate(CoreWorkspaceRoute route) {
        if (!NavigationBus.showCoreWorkspace(route)) {
            UiAlerts.info("Open this destination from the sidebar after the dashboard has loaded.");
        }
    }
}
