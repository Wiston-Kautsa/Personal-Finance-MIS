package com.wk.pfmis.utils;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ReadableTextSupport {
    private ReadableTextSupport() {
    }

    public static void apply(Node root) {
        if (root == null) {
            return;
        }
        convertActionRows(root);
        normalize(root);
    }

    private static void convertActionRows(Node node) {
        if (node instanceof ScrollPane scrollPane) {
            convertActionRows(scrollPane.getContent());
        }
        if (node instanceof TitledPane titledPane) {
            convertActionRows(titledPane.getContent());
        }
        if (node instanceof TabPane tabPane) {
            for (Tab tab : tabPane.getTabs()) {
                addTooltip(tab);
                convertActionRows(tab.getContent());
            }
        }
        if (node instanceof ToolBar toolBar) {
            List<Node> children = new ArrayList<>(toolBar.getItems());
            for (Node child : children) {
                convertActionRows(child);
            }
        }
        if (!(node instanceof Pane pane)) {
            return;
        }

        List<Node> children = new ArrayList<>(pane.getChildren());
        for (int index = 0; index < children.size(); index++) {
            Node child = children.get(index);
            if (child instanceof HBox hBox && shouldStackModuleHeader(hBox)) {
                VBox header = stackedModuleHeader(hBox);
                pane.getChildren().set(index, header);
                convertActionRows(header);
            } else if (child instanceof HBox hBox && shouldWrapActionRow(hBox)) {
                FlowPane flowPane = wrappedActionRow(hBox);
                pane.getChildren().set(index, flowPane);
                convertActionRows(flowPane);
            } else {
                convertActionRows(child);
            }
        }
    }

    private static VBox stackedModuleHeader(HBox hBox) {
        VBox header = new VBox(10);
        header.setManaged(hBox.isManaged());
        header.setVisible(hBox.isVisible());
        header.setDisable(hBox.isDisable());
        header.setId(hBox.getId());
        header.getStyleClass().setAll(hBox.getStyleClass());
        header.setPadding(hBox.getPadding() == null ? Insets.EMPTY : hBox.getPadding());
        header.setMaxWidth(Double.MAX_VALUE);
        copyLayoutConstraints(hBox, header);

        FlowPane actions = new FlowPane();
        actions.setHgap(Math.max(8, hBox.getSpacing()));
        actions.setVgap(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setMaxWidth(Double.MAX_VALUE);
        actions.setPrefWrapLength(Math.max(420, hBox.getPrefWidth() > 0 ? hBox.getPrefWidth() : 900));
        actions.getStyleClass().add("responsive-action-row");

        List<Node> leading = new ArrayList<>();
        List<Node> movedChildren = new ArrayList<>(hBox.getChildren());
        hBox.getChildren().clear();
        for (Node child : movedChildren) {
            if (child instanceof ButtonBase) {
                actions.getChildren().add(child);
            } else if (child instanceof Region region && isSpacer(region)) {
                // Spacers are only useful in a single-line HBox. The wrapping row does not need them.
            } else {
                leading.add(child);
            }
        }
        header.getChildren().addAll(leading);
        if (!actions.getChildren().isEmpty()) {
            header.getChildren().add(actions);
        }
        return header;
    }

    private static FlowPane wrappedActionRow(HBox hBox) {
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(Math.max(8, hBox.getSpacing()));
        flowPane.setVgap(8);
        flowPane.setAlignment(hBox.getAlignment() == null ? Pos.CENTER_LEFT : hBox.getAlignment());
        flowPane.setManaged(hBox.isManaged());
        flowPane.setVisible(hBox.isVisible());
        flowPane.setDisable(hBox.isDisable());
        flowPane.setId(hBox.getId());
        flowPane.getStyleClass().setAll(hBox.getStyleClass());
        if (!flowPane.getStyleClass().contains("responsive-action-row")) {
            flowPane.getStyleClass().add("responsive-action-row");
        }
        flowPane.setPadding(hBox.getPadding() == null ? Insets.EMPTY : hBox.getPadding());
        flowPane.setMaxWidth(Double.MAX_VALUE);
        flowPane.setPrefWrapLength(Math.max(360, hBox.getPrefWidth() > 0 ? hBox.getPrefWidth() : 900));
        copyLayoutConstraints(hBox, flowPane);

        List<Node> movedChildren = new ArrayList<>(hBox.getChildren());
        hBox.getChildren().clear();
        flowPane.getChildren().setAll(movedChildren);
        return flowPane;
    }

    private static boolean shouldWrapActionRow(HBox hBox) {
        long buttons = hBox.getChildren().stream()
                .filter(ReadableTextSupport::isReadableActionControl)
                .count();
        if (buttons < 2) {
            return false;
        }
        if (hasActionStyle(hBox)) {
            return true;
        }
        long blockingChildren = hBox.getChildren().stream()
                .filter(child -> !(child instanceof ButtonBase)
                        && !(child instanceof Label)
                        && !(child instanceof Region)
                        && !(child instanceof ComboBoxBase<?>)
                        && !(child instanceof TextInputControl)
                        && !(child instanceof ProgressIndicator))
                .count();
        return blockingChildren == 0;
    }

    private static boolean shouldStackModuleHeader(HBox hBox) {
        boolean moduleHeader = hBox.getStyleClass().stream()
                .anyMatch(style -> "module-header".equalsIgnoreCase(style));
        if (!moduleHeader) {
            return false;
        }
        long buttons = hBox.getChildren().stream()
                .filter(ReadableTextSupport::isReadableActionControl)
                .count();
        return buttons >= 2;
    }

    private static boolean isSpacer(Region region) {
        return region.getClass() == Region.class
                && (HBox.getHgrow(region) == Priority.ALWAYS || VBox.getVgrow(region) == Priority.ALWAYS);
    }

    private static boolean hasActionStyle(Node node) {
        return node.getStyleClass().stream()
                .map(style -> style.toLowerCase(Locale.ENGLISH))
                .anyMatch(style -> style.contains("action")
                        || style.contains("toolbar")
                        || style.contains("button-row")
                        || style.contains("form-actions")
                        || style.contains("simple-actions"));
    }

    private static boolean isReadableActionControl(Node node) {
        if (node instanceof ButtonBase button) {
            String text = safe(button.getText());
            return !text.isBlank() && !"…".equals(text) && !"...".equals(text);
        }
        return false;
    }

    private static void normalize(Node node) {
        if (node instanceof ButtonBase button) {
            normalizeButton(button);
        } else if (node instanceof Label label) {
            normalizeLabel(label);
        } else if (node instanceof CheckBox checkBox) {
            normalizeToggleLabel(checkBox);
        } else if (node instanceof RadioButton radioButton) {
            normalizeToggleLabel(radioButton);
        } else if (node instanceof ComboBoxBase<?> comboBoxBase) {
            comboBoxBase.setMinWidth(Math.max(140, comboBoxBase.getMinWidth()));
        } else if (node instanceof TableView<?> tableView) {
            tableView.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        }

        if (node instanceof ScrollPane scrollPane) {
            normalize(scrollPane.getContent());
        }
        if (node instanceof TitledPane titledPane) {
            normalize(titledPane.getContent());
        }
        if (node instanceof TabPane tabPane) {
            for (Tab tab : tabPane.getTabs()) {
                addTooltip(tab);
                normalize(tab.getContent());
            }
        }
        if (node instanceof ToolBar toolBar) {
            for (Node child : toolBar.getItems()) {
                normalize(child);
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                normalize(child);
            }
        }
    }

    private static void normalizeButton(ButtonBase button) {
        String text = safe(button.getText());
        if (text.isBlank()) {
            return;
        }
        button.setTextOverrun(OverrunStyle.CLIP);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxWidth(Region.USE_COMPUTED_SIZE);
        button.setWrapText(false);
        if (button.getContentDisplay() == null) {
            button.setContentDisplay(ContentDisplay.CENTER);
        }
        addTooltip(button, text);
    }

    private static void normalizeToggleLabel(ButtonBase control) {
        String text = safe(control.getText());
        if (text.isBlank()) {
            return;
        }
        control.setMinWidth(Region.USE_PREF_SIZE);
        control.setTextOverrun(OverrunStyle.CLIP);
        control.setWrapText(true);
        addTooltip(control, text);
    }

    private static void normalizeLabel(Label label) {
        String text = safe(label.getText());
        if (text.isBlank()) {
            return;
        }
        if (isImportantLabel(label)) {
            label.setWrapText(true);
            label.setTextOverrun(OverrunStyle.CLIP);
            label.setMinWidth(Region.USE_COMPUTED_SIZE);
            addTooltip(label, text);
        }
    }

    private static boolean isImportantLabel(Label label) {
        return label.getStyleClass().stream()
                .map(style -> style.toLowerCase(Locale.ENGLISH))
                .anyMatch(style -> style.contains("title")
                        || style.contains("heading")
                        || style.contains("field-label")
                        || style.contains("nav-section")
                        || style.contains("metric-title")
                        || style.contains("status-text")
                        || style.contains("form-note"));
    }

    private static void addTooltip(ButtonBase button, String text) {
        if (button.getTooltip() == null && text.length() > 0) {
            button.setTooltip(new Tooltip(text));
        }
    }

    private static void addTooltip(Label label, String text) {
        if (label.getTooltip() == null && text.length() > 18) {
            label.setTooltip(new Tooltip(text));
        }
    }

    private static void addTooltip(Tab tab) {
        if (tab == null) {
            return;
        }
        String text = safe(tab.getText());
        if (!text.isBlank() && tab.getTooltip() == null) {
            tab.setTooltip(new Tooltip(text));
        }
    }

    private static void copyLayoutConstraints(Node source, Node target) {
        VBox.setVgrow(target, VBox.getVgrow(source));
        VBox.setMargin(target, VBox.getMargin(source));
        HBox.setHgrow(target, HBox.getHgrow(source));
        HBox.setMargin(target, HBox.getMargin(source));
        StackPane.setAlignment(target, StackPane.getAlignment(source));
        StackPane.setMargin(target, StackPane.getMargin(source));
        BorderPane.setAlignment(target, BorderPane.getAlignment(source));
        BorderPane.setMargin(target, BorderPane.getMargin(source));
        AnchorPane.setTopAnchor(target, AnchorPane.getTopAnchor(source));
        AnchorPane.setRightAnchor(target, AnchorPane.getRightAnchor(source));
        AnchorPane.setBottomAnchor(target, AnchorPane.getBottomAnchor(source));
        AnchorPane.setLeftAnchor(target, AnchorPane.getLeftAnchor(source));

        Integer column = GridPane.getColumnIndex(source);
        Integer row = GridPane.getRowIndex(source);
        Integer columnSpan = GridPane.getColumnSpan(source);
        Integer rowSpan = GridPane.getRowSpan(source);
        Priority hgrow = GridPane.getHgrow(source);
        Priority vgrow = GridPane.getVgrow(source);
        HPos halignment = GridPane.getHalignment(source);
        VPos valignment = GridPane.getValignment(source);
        Insets margin = GridPane.getMargin(source);
        if (column != null) {
            GridPane.setColumnIndex(target, column);
        }
        if (row != null) {
            GridPane.setRowIndex(target, row);
        }
        if (columnSpan != null) {
            GridPane.setColumnSpan(target, columnSpan);
        }
        if (rowSpan != null) {
            GridPane.setRowSpan(target, rowSpan);
        }
        if (hgrow != null) {
            GridPane.setHgrow(target, hgrow);
        }
        if (vgrow != null) {
            GridPane.setVgrow(target, vgrow);
        }
        if (halignment != null) {
            GridPane.setHalignment(target, halignment);
        }
        if (valignment != null) {
            GridPane.setValignment(target, valignment);
        }
        if (margin != null) {
            GridPane.setMargin(target, margin);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
