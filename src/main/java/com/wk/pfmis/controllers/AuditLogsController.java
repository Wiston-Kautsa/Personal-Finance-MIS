package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AiInteractionRecord;
import com.wk.pfmis.models.SystemLogRecord;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.util.List;

public class AuditLogsController {
    @FXML private Label auditSummaryLabel;
    @FXML private Label auditScopeLabel;
    @FXML private TableView<SystemLogRecord> systemLogTable;
    @FXML private TableColumn<SystemLogRecord, String> systemLogDateColumn;
    @FXML private TableColumn<SystemLogRecord, String> systemLogModuleColumn;
    @FXML private TableColumn<SystemLogRecord, String> systemLogActionColumn;
    @FXML private TableColumn<SystemLogRecord, String> systemLogSeverityColumn;
    @FXML private TableColumn<SystemLogRecord, String> systemLogDetailsColumn;
    @FXML private TableView<AiInteractionRecord> interactionTable;
    @FXML private TableColumn<AiInteractionRecord, String> interactionDateColumn;
    @FXML private TableColumn<AiInteractionRecord, String> interactionModuleColumn;
    @FXML private TableColumn<AiInteractionRecord, String> interactionActionColumn;
    @FXML private TableColumn<AiInteractionRecord, String> interactionProviderColumn;
    @FXML private TableColumn<AiInteractionRecord, String> interactionStatusColumn;

    private final DatabaseHandler database = DatabaseHandler.getInstance();

    @FXML
    public void initialize() {
        configureSystemLogTable();
        configureInteractionLogTable();
        configureContextMenus();
        refresh();
    }

    @FXML
    private void refresh() {
        var systemLogs = database.listSystemLogHistory(200);
        var interactionLogs = database.listAiInteractionHistory(200);
        systemLogTable.setItems(FXCollections.observableArrayList(systemLogs));
        interactionTable.setItems(FXCollections.observableArrayList(interactionLogs));
        auditSummaryLabel.setText("System events: " + systemLogs.size() + " | Smart Analysis requests: " + interactionLogs.size());
        auditScopeLabel.setText("Records include application startup, shutdown, data changes, backups, restore actions, maintenance checks, and Smart Analysis requests.");
    }

    private void configureSystemLogTable() {
        systemLogDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCreatedAt()));
        systemLogModuleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getModuleName()));
        systemLogActionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getActionName()));
        systemLogSeverityColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getSeverity()));
        systemLogDetailsColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDetails()));
    }

    private void configureInteractionLogTable() {
        interactionDateColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getCreatedAt()));
        interactionModuleColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getModuleName()));
        interactionActionColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getActionName()));
        interactionProviderColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getProviderName()));
        interactionStatusColumn.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStatus()));
    }

    private void configureContextMenus() {
        TableActions.installRowContextMenu(systemLogTable, this::systemLogMenuItems);
        TableActions.installRowContextMenu(interactionTable, this::interactionMenuItems);
    }

    private List<javafx.scene.control.MenuItem> systemLogMenuItems(SystemLogRecord record) {
        return List.of(
                TableActions.menuItem("View System Event", () -> viewSystemLog(record)),
                TableActions.separator(),
                TableActions.copyRowItem(systemLogTable, record),
                TableActions.exportTableItem(systemLogTable, "System Audit Log"),
                TableActions.printTableItem(systemLogTable, "System Audit Log"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private List<javafx.scene.control.MenuItem> interactionMenuItems(AiInteractionRecord record) {
        return List.of(
                TableActions.menuItem("View Smart Analysis Event", () -> viewInteractionLog(record)),
                TableActions.separator(),
                TableActions.copyRowItem(interactionTable, record),
                TableActions.exportTableItem(interactionTable, "Smart Analysis Audit Log"),
                TableActions.printTableItem(interactionTable, "Smart Analysis Audit Log"),
                TableActions.refreshItem(this::refresh)
        );
    }

    private void viewSystemLog(SystemLogRecord record) {
        if (record == null) {
            return;
        }
        UiAlerts.info(
                "Date: " + record.getCreatedAt()
                        + "\nModule: " + record.getModuleName()
                        + "\nAction: " + record.getActionName()
                        + "\nSeverity: " + record.getSeverity()
                        + "\nDetails: " + record.getDetails()
        );
    }

    private void viewInteractionLog(AiInteractionRecord record) {
        if (record == null) {
            return;
        }
        UiAlerts.info(
                "Date: " + record.getCreatedAt()
                        + "\nModule: " + record.getModuleName()
                        + "\nAction: " + record.getActionName()
                        + "\nProvider: " + record.getProviderName()
                        + "\nStatus: " + record.getStatus()
        );
    }
}
