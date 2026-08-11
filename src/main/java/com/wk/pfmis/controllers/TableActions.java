package com.wk.pfmis.controllers;

import com.wk.pfmis.utils.ExportPathService;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;
import javafx.stage.Window;
import javafx.print.PrinterJob;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

final class TableActions {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private TableActions() {
    }

    static <S> void installRowContextMenu(TableView<S> table, Function<S, List<MenuItem>> menuFactory) {
        installRowContextMenu(table, menuFactory, null);
    }

    static <S> void installRowContextMenu(TableView<S> table, Function<S, List<MenuItem>> menuFactory, Consumer<S> doubleClickAction) {
        if (table == null || menuFactory == null) {
            return;
        }
        configureScrollableTable(table);
        table.setRowFactory(tableView -> {
            TableRow<S> row = new TableRow<>();
            row.setOnMousePressed(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.SECONDARY) {
                    table.getSelectionModel().clearAndSelect(row.getIndex());
                }
            });
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty()
                        && event.getButton() == MouseButton.PRIMARY
                        && event.getClickCount() == 2
                        && doubleClickAction != null) {
                    table.getSelectionModel().clearAndSelect(row.getIndex());
                    doubleClickAction.accept(row.getItem());
                    event.consume();
                }
            });
            row.setOnContextMenuRequested(event -> {
                if (row.isEmpty()) {
                    return;
                }
                table.getSelectionModel().clearAndSelect(row.getIndex());
                List<MenuItem> menuItems = compactMenuItems(menuFactory.apply(row.getItem()));
                if (menuItems.isEmpty()) {
                    return;
                }
                ContextMenu contextMenu = new ContextMenu(menuItems.toArray(MenuItem[]::new));
                contextMenu.show(row, event.getScreenX(), event.getScreenY());
                event.consume();
            });
            return row;
        });
    }

    static void configureScrollableTable(TableView<?> table) {
        if (table == null) {
            return;
        }
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        if (!table.getStyleClass().contains("project-list-table")) {
            table.getStyleClass().add("project-list-table");
        }
        for (TableColumn<?, ?> column : table.getColumns()) {
            configureReadableColumn(column);
        }
    }

    private static void configureReadableColumn(TableColumn<?, ?> column) {
        if (column == null) {
            return;
        }
        double prefWidth = column.getPrefWidth();
        double currentWidth = prefWidth > 0 ? prefWidth : Math.max(column.getWidth(), 100);
        double minWidth = readableMinimumWidth(column.getText(), currentWidth);
        column.setMinWidth(Math.max(column.getMinWidth(), minWidth));
        if (prefWidth <= 0 || prefWidth < minWidth) {
            column.setPrefWidth(minWidth);
        }
        for (TableColumn<?, ?> child : column.getColumns()) {
            configureReadableColumn(child);
        }
    }

    private static double readableMinimumWidth(String header, double currentWidth) {
        String text = header == null ? "" : header.trim();
        double width = Math.max(72, currentWidth);
        if (!text.isBlank()) {
            width = Math.max(width, text.length() * 8.5 + 42);
        }

        String normalized = text.toLowerCase(Locale.ENGLISH);
        if (normalized.contains("date") || normalized.contains("month") || normalized.contains("login")) {
            width = Math.max(width, 125);
        }
        if (normalized.contains("amount")
                || normalized.contains("balance")
                || normalized.contains("budget")
                || normalized.contains("spent")
                || normalized.contains("remaining")
                || normalized.contains("limit")
                || normalized.contains("price")
                || normalized.contains("missing")
                || normalized.contains("reached")) {
            width = Math.max(width, 145);
        }
        if (normalized.contains("person")
                || normalized.contains("institution")
                || normalized.contains("relationship")
                || normalized.contains("provider")
                || normalized.contains("category")
                || normalized.contains("account")) {
            width = Math.max(width, 170);
        }
        if (normalized.contains("email")) {
            width = Math.max(width, 240);
        }
        if (normalized.contains("reference")) {
            width = Math.max(width, 170);
        }
        if (normalized.contains("notes") || normalized.contains("reason") || normalized.contains("details")) {
            width = Math.max(width, 340);
        }
        if (normalized.contains("action")) {
            width = Math.max(width, 420);
        }
        return width;
    }

    static MenuItem menuItem(String text, Runnable action) {
        MenuItem item = new MenuItem(text);
        item.setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
        return item;
    }

    static MenuItem separator() {
        return new SeparatorMenuItem();
    }

    static <S> MenuItem copyRowItem(TableView<S> table, S item) {
        return menuItem("Copy Row", () -> copyRowToClipboard(table, item));
    }

    static <S> MenuItem exportTableItem(TableView<S> table, String baseFileName) {
        return menuItem("Export Table", () -> exportVisibleTableToCsv(table, baseFileName));
    }

    static MenuItem printTableItem(TableView<?> table, String title) {
        return menuItem("Print Table", () -> printTable(table, title));
    }

    static MenuItem refreshItem(Runnable refreshAction) {
        return menuItem("Refresh", refreshAction);
    }

    static <S> void exportVisibleTableToCsv(TableView<S> table, String baseFileName) {
        if (table == null || table.getItems().isEmpty()) {
            UiAlerts.info("No records to export.");
            return;
        }

        List<TableColumn<S, ?>> columns = visibleColumns(table);
        if (columns.isEmpty()) {
            UiAlerts.info("No visible table columns to export.");
            return;
        }

        try {
            Path file = ExportPathService.resolveExportFile(defaultFileName(baseFileName));
            writeCsv(table, columns, file);
            UiAlerts.info("Exported " + table.getItems().size() + " record(s)." + System.lineSeparator()
                    + System.lineSeparator()
                    + "Saved to:" + System.lineSeparator()
                    + file.toAbsolutePath().normalize());
        } catch (IOException exception) {
            UiAlerts.error("Failed to export records", exception);
        }
    }

    static void printTable(TableView<?> table, String title) {
        if (table == null || table.getItems().isEmpty()) {
            UiAlerts.info("No records to print.");
            return;
        }
        printNode(table, title == null || title.isBlank() ? "Records" : title);
    }

    private static <S> List<TableColumn<S, ?>> visibleColumns(TableView<S> table) {
        return table.getVisibleLeafColumns().stream()
                .filter(column -> column.getText() != null && !column.getText().isBlank())
                .toList();
    }

    private static <S> void copyRowToClipboard(TableView<S> table, S item) {
        if (table == null || item == null) {
            return;
        }
        List<TableColumn<S, ?>> columns = visibleColumns(table);
        if (columns.isEmpty()) {
            UiAlerts.info("No visible table columns to copy.");
            return;
        }
        String text = columns.stream()
                .map(column -> column.getText() + ": " + nullToBlank(cellText(column, item)))
                .collect(Collectors.joining(System.lineSeparator()));
        ClipboardContent content = new ClipboardContent();
        content.putString(text);
        Clipboard.getSystemClipboard().setContent(content);
        UiAlerts.info("Row copied to clipboard.");
    }

    private static <S> void writeCsv(TableView<S> table, List<TableColumn<S, ?>> columns, Path file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            writer.write(columns.stream()
                    .map(column -> csvCell(column.getText()))
                    .collect(Collectors.joining(",")));
            writer.newLine();
            for (S item : table.getItems()) {
                writer.write(columns.stream()
                        .map(column -> csvCell(cellText(column, item)))
                        .collect(Collectors.joining(",")));
                writer.newLine();
            }
        }
    }

    private static <S> String cellText(TableColumn<S, ?> column, S item) {
        Object value = column.getCellData(item);
        return value == null ? "" : value.toString();
    }

    private static String csvCell(String value) {
        String text = neutralizeSpreadsheetFormula(value == null ? "" : value);
        if (text.contains("\"") || text.contains(",") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String neutralizeSpreadsheetFormula(String text) {
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == ' ') {
                continue;
            }
            return switch (character) {
                case '=', '+', '-', '@', '\t', '\r', '\n' -> "'" + text;
                default -> text;
            };
        }
        return text;
    }

    private static List<MenuItem> compactMenuItems(List<MenuItem> menuItems) {
        if (menuItems == null || menuItems.isEmpty()) {
            return List.of();
        }
        List<MenuItem> compact = new ArrayList<>();
        boolean previousSeparator = true;
        for (MenuItem item : menuItems) {
            if (item == null) {
                continue;
            }
            boolean separator = item instanceof SeparatorMenuItem;
            if (separator && previousSeparator) {
                continue;
            }
            compact.add(item);
            previousSeparator = separator;
        }
        if (!compact.isEmpty() && compact.get(compact.size() - 1) instanceof SeparatorMenuItem) {
            compact.remove(compact.size() - 1);
        }
        return compact;
    }

    private static void printNode(Node node, String title) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            UiAlerts.info("No printer is available.");
            return;
        }

        Window owner = owner(node);
        if (owner != null && !job.showPrintDialog(owner)) {
            return;
        }

        Scale scale = printScale(node, job);
        node.getTransforms().add(scale);
        boolean printed;
        try {
            printed = job.printPage(node);
        } finally {
            node.getTransforms().remove(scale);
        }

        if (printed) {
            job.endJob();
            UiAlerts.info(title + " sent to printer.");
        } else {
            UiAlerts.info("Print job was cancelled or failed.");
        }
    }

    private static Scale printScale(Node node, PrinterJob job) {
        double printableWidth = job.getJobSettings().getPageLayout().getPrintableWidth();
        double nodeWidth = node.getBoundsInParent().getWidth();
        double scale = nodeWidth <= 0 ? 1 : Math.min(1, printableWidth / nodeWidth);
        return new Scale(scale, scale);
    }

    private static String defaultFileName(String baseFileName) {
        String base = baseFileName == null || baseFileName.isBlank() ? "records" : baseFileName;
        String safeBase = base.trim()
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_|_$", "");
        if (safeBase.isBlank()) {
            safeBase = "Records";
        }
        return "PFMIS_" + safeBase + "_" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".csv";
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static Window owner(Node node) {
        return node == null || node.getScene() == null ? null : node.getScene().getWindow();
    }
}
