package com.wk.pfmis.controllers;

import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.BackupRecord;
import com.wk.pfmis.models.SystemLogRecord;
import com.wk.pfmis.security.UserSession;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class DataRecordsPolicyController {
    private static final DateTimeFormatter EXPORT_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ENGLISH);

    @FXML private Label policyTitleLabel;
    @FXML private Label policySubtitleLabel;
    @FXML private Label policyStatusLabel;
    @FXML private Label phaseLabel;
    @FXML private Label ownerLabel;
    @FXML private Label riskLabel;
    @FXML private Label impactLabel;
    @FXML private FlowPane workflowStepsPane;
    @FXML private FlowPane wireframeFieldsPane;
    @FXML private FlowPane processActionsPane;
    @FXML private TextArea lifecycleArea;
    @FXML private TextArea controlsArea;
    @FXML private TextArea permissionsArea;
    @FXML private TextArea evidenceArea;
    @FXML private TextArea resultArea;
    @FXML private Button primaryActionButton;
    @FXML private Button requestActionButton;
    @FXML private Button backupActionButton;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private String pendingArea;
    private String currentArea = "Data Governance";

    @FXML
    public void initialize() {
        selectArea(pendingArea == null || pendingArea.isBlank() ? currentArea : pendingArea);
    }

    public void selectArea(String area) {
        pendingArea = area;
        currentArea = area == null || area.isBlank() ? "Data Governance" : area.trim();
        if (policyTitleLabel == null) {
            return;
        }
        applySpec(specFor(currentArea));
    }

    @FXML
    private void refresh() {
        if (policyTitleLabel != null) {
            applySpec(specFor(currentArea));
        }
    }

    @FXML
    private void runPrimaryAction() {
        runProcessAction(primaryActionButton == null ? "Review" : primaryActionButton.getText());
    }

    private void runProcessAction(String action) {
        String cleanAction = action == null || action.isBlank() ? "Review Process" : action.trim();
        try {
            if (isBackupAction(cleanAction)) {
                backupNow();
                logProcessAction(cleanAction, "INFO", "Safety backup action completed from Data and Records.");
                return;
            } else if (isExportAction(cleanAction)) {
                resultArea.setText(exportProcessEvidence(cleanAction));
                policyStatusLabel.setText("Process evidence exported.");
                return;
            } else if (requiresControlledWorkflow(cleanAction)) {
                prepareControlledWorkflow(cleanAction);
                return;
            } else if (isDataQualityArea(currentArea) || isDataQualityAction(cleanAction)) {
                resultArea.setText(actionHeader(cleanAction)
                        + database.dataQualitySummary()
                        + System.lineSeparator()
                        + "Governance result: exceptions must be corrected through lifecycle controls before reports or Smart Analysis conclusions are trusted.");
                policyStatusLabel.setText("Data quality process completed. No record was changed.");
            } else if (isMaintenanceArea(currentArea)) {
                if (!UserSession.isSuperAdmin()) {
                    UiAlerts.info("Only a Super Administrator may preview Data Maintenance operations.");
                    return;
                }
                resultArea.setText(actionHeader(cleanAction) + maintenancePreview());
                policyStatusLabel.setText("Maintenance impact preview loaded. No records were changed.");
            } else if (isAuditArea(currentArea)) {
                resultArea.setText(actionHeader(cleanAction) + historySummary("Audit evidence for " + currentArea));
                policyStatusLabel.setText("Audit evidence loaded.");
            } else if (isSyncArea(currentArea)) {
                resultArea.setText(actionHeader(cleanAction) + syncRules() + processWireframeNarrative(cleanAction));
                policyStatusLabel.setText("Sync operating rules loaded.");
            } else if (isImportArea(currentArea)) {
                resultArea.setText(actionHeader(cleanAction) + importWorkflow() + processWireframeNarrative(cleanAction));
                policyStatusLabel.setText("Import workflow preview loaded. No records were imported.");
            } else {
                resultArea.setText(actionHeader(cleanAction) + recordLifecycleEvidence() + processWireframeNarrative(cleanAction));
                policyStatusLabel.setText("Record lifecycle guidance loaded.");
            }
            logProcessAction(cleanAction, "INFO", "Safe process action completed; no destructive action executed.");
        } catch (RuntimeException exception) {
            logProcessAction(cleanAction + " failed", "ERROR", rootMessage(exception));
            UiAlerts.error("Failed to run Data and Records action", exception);
        }
    }

    @FXML
    private void createGovernanceRequest() {
        if (isMaintenanceArea(currentArea) && !UserSession.isSuperAdmin()) {
            UiAlerts.info("Data Maintenance requests must be reviewed by a Super Administrator.");
            return;
        }
        database.recordSystemLog(
                "Data And Records",
                "Governance request",
                isMaintenanceArea(currentArea) ? "WARNING" : "INFO",
                currentArea + " request recorded for review. No data was changed."
        );
        resultArea.setText("Request recorded for " + currentArea + ".\n\n"
                + "Next required controls:\n"
                + "- Review scope and affected records\n"
                + "- Capture reason and approval reference\n"
                + "- Create or verify recovery backup for high-impact actions\n"
                + "- Execute only through the controlled workflow\n"
                + "- Preserve immutable audit evidence");
        policyStatusLabel.setText("Governance request recorded.");
    }

    @FXML
    private void backupNow() {
        try {
            BackupRecord backup = database.createBackup(DatabaseHandler.defaultBackupDirectory(), "data-records-safety-backup");
            resultArea.setText("Safety backup created:\n"
                    + backup.getBackupFile() + "\n\nChecksum:\n"
                    + backup.getChecksum());
            policyStatusLabel.setText("Safety backup created.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to create Data and Records backup", exception);
        }
    }

    @FXML
    private void viewRecentHistory() {
        resultArea.setText(historySummary("Recent Data and Records history"));
        policyStatusLabel.setText("Recent history loaded.");
    }

    private void applySpec(PolicySpec spec) {
        policyTitleLabel.setText(spec.title());
        policySubtitleLabel.setText(spec.description());
        phaseLabel.setText(phaseFor(currentArea));
        ownerLabel.setText(ownerFor(currentArea));
        riskLabel.setText(riskFor(currentArea));
        impactLabel.setText(impactFor(currentArea));
        renderWorkflowSteps(spec.lifecycle());
        renderWireframeFields(currentArea);
        renderProcessButtons(spec.controls());
        lifecycleArea.setText(spec.lifecycle());
        controlsArea.setText(spec.controls());
        permissionsArea.setText(spec.permissions());
        evidenceArea.setText(spec.evidence());
        primaryActionButton.setText(spec.primaryAction());
        requestActionButton.setText(spec.secondaryAction());
        backupActionButton.setVisible(spec.backupVisible());
        backupActionButton.setManaged(spec.backupVisible());
        resultArea.setText(spec.initialResult());
        policyStatusLabel.setText("Ready. This page is governance-first and does not bypass report, audit or maintenance controls.");
    }

    private PolicySpec specFor(String area) {
        return switch (area) {
            case "File Import" -> spec(
                    "File Import",
                    "Controlled intake for bank statements, mobile-money statements, CSV transaction files, opening balances, budgets, project activities, goals, loans, categories and payment methods.",
                    lines("Select file -> Select import type -> Map columns -> Validate format -> Detect duplicates -> Preview financial effect -> Correct or reject invalid rows -> Approve import -> Commit valid records -> Reconcile totals -> Record import audit."),
                    lines("Download Template", "Select File", "Map Columns", "Validate", "Preview", "Commit Valid Records", "Download Error File", "Cancel Import", "Roll Back Import Batch"),
                    lines("Administrators may prepare and validate imports. Posting important records requires an authorised approval role. Physical rollback or purge is Super Administrator controlled."),
                    lines("Duplicate checks must compare date, amount, account, description, reference number, payment method, person or organisation, imported source and existing record identifier."),
                    "Preview Import Workflow",
                    "Create Import Request",
                    true
            );
            case "Import Validation" -> spec(
                    "Import Validation",
                    "Validate imported rows before they are allowed to affect balances or reports.",
                    lines("Validate format -> Detect duplicates -> Quarantine invalid rows -> Produce error file -> Approve clean batch."),
                    lines("Validate Format", "Check Duplicates", "Preview Financial Effect", "Reject Invalid Rows", "Download Error File"),
                    lines("Invalid rows must not be written to operational tables. Administrators may correct rows; approval controls posting."),
                    lines("Validation evidence should include row count, valid count, rejected count, duplicate count, checksum, user and timestamp."),
                    "Run Validation Preview",
                    "Create Validation Request",
                    true
            );
            case "Posting and Approval" -> spec(
                    "Posting and Approval",
                    "Separate draft intake from records that may affect ledgers, reports and recommendations.",
                    lines("Draft -> Validate -> Review -> Approve -> Post -> Freeze -> Use in Report."),
                    lines("Submit for Approval", "Approve", "Reject", "Post Approved Batch", "View Approval Reference"),
                    lines("Administrators may submit and review within scope. Important posting approvals should be restricted to authorised roles."),
                    lines("Approval records must include approver, reason, affected records, affected amount, timestamp and backup reference when required."),
                    "Preview Posting Controls",
                    "Create Approval Request",
                    true
            );
            case "Rejected Records" -> spec(
                    "Rejected Records",
                    "Keep failed or rejected intake rows visible until corrected, rejected permanently or archived under policy.",
                    lines("Import -> Validate -> Reject row -> Correct or retain with reason -> Audit disposition."),
                    lines("Review Rejected Rows", "Correct Selected Row", "Reject Batch", "Export Error File", "View Rejection Reason"),
                    lines("Rejected records do not affect balances or reports. They remain evidence for import quality and correction history."),
                    lines("Rejected-row evidence should preserve raw value, mapped value, validation message, user, source file and batch ID."),
                    "Review Rejection Rules",
                    "Create Correction Request",
                    false
            );
            case "Import History" -> spec(
                    "Import History",
                    "Track every import batch, validation result, posting decision and rollback request.",
                    lines("Batch created -> Validation results -> Approval status -> Commit status -> Reconciliation result -> Audit record."),
                    lines("View Batch", "View Checksum", "View Posted Records", "Export Import Audit", "Request Rollback"),
                    lines("Ordinary users may see their own imports. Super Administrators may review all workspace import history."),
                    lines("History should retain source file, checksum, import type, counts, status, creator, creation time and posted time."),
                    "Load Import History Rules",
                    "Create Import Audit Request",
                    false
            );
            case "Active Records", "Draft Records", "Frozen Records", "Cancelled and Reversed", "Archived Records", "Correction Requests" ->
                    recordsControlSpec(area);
            case "Data Health Overview", "Missing Information", "Duplicate Records", "Account Reconciliation", "Relationship Errors", "Exceptions" ->
                    dataQualitySpec(area);
            case "Financial Record History", "Administrative Actions", "Data Disposal History", "Audit Export" ->
                    auditSpec(area);
            case "Pending Queue", "Failed Records", "Conflicts", "Quarantine", "Sync History" ->
                    syncSpec(area);
            case "Record Disposal", "Clear Test or Demo Data", "Purge Archived Records", "Reset Workspace", "Delete Workspace", "Maintenance History" ->
                    maintenanceSpec(area);
            default -> spec(
                    "Data Governance",
                    "Configure the data lifecycle from capture through audit, backup and recovery.",
                    lines("Capture -> Validate -> Approve/Post -> Use -> Correct -> Freeze -> Cancel/Reverse -> Archive -> Sync -> Retain -> Dispose -> Recover -> Audit."),
                    lines("Run Full Data Check", "Create Correction Request", "View History", "Backup Now"),
                    lines("Physical deletion is Super Administrator-only. Freezing locks a valid record but does not remove it from calculations."),
                    lines("Data governance evidence should explain what changed, who changed it, why, approval reference, result and affected reports."),
                    "Review Governance",
                    "Create Governance Request",
                    true
            );
        };
    }

    private PolicySpec recordsControlSpec(String area) {
        String statusRule = switch (area) {
            case "Frozen Records" -> "Freeze means valid but locked. Frozen records remain included in balances and reports.";
            case "Cancelled and Reversed" -> "Cancel invalidates a record before it should affect totals. Reverse preserves history and neutralises posted financial effect.";
            case "Archived Records" -> "Archive hides old records from normal operations but keeps historical reporting evidence.";
            case "Correction Requests" -> "Correction requests capture reason, affected record, requested action, approval status and outcome.";
            case "Draft Records" -> "Draft records are editable and should not affect financial reports until posted.";
            default -> "Posted and active records remain part of operational reporting unless cancelled, reversed or archived under policy.";
        };
        return spec(
                area,
                "Manage record lifecycle without rewriting financial history.",
                lines("DRAFT -> POSTED -> FROZEN -> CANCELLED / REVERSED -> ARCHIVED -> PENDING_PURGE -> PURGED", statusRule),
                lines("Freeze Selected", "Freeze Batch", "View Freeze Reason", "Request Unfreeze", "Request Cancellation", "Request Reversal", "View Record History"),
                lines("Administrators may freeze, archive, deactivate and request correction. Super Administrators approve cancellation, reversal, restore and purge."),
                lines("Status history should record entity type, entity ID, old status, new status, reason, changed by, changed at and approval ID."),
                "Review Lifecycle Rules",
                "Create Correction Request",
                false
        );
    }

    private PolicySpec dataQualitySpec(String area) {
        return spec(
                area,
                "Check whether financial data is reliable enough for reports and Smart Analysis recommendations.",
                lines("Run checks -> classify issue -> review exception -> correct record or create request -> verify -> audit resolution."),
                lines("Run Full Data Check", "Check Duplicates", "Reconcile Account", "Review Exceptions", "Resolve Selected Issue", "Freeze Suspicious Record", "Create Correction Request", "Export Data Quality Report"),
                lines("Administrators may review and correct permitted records. Super Administrators approve destructive remediation and full conflict resolution."),
                lines("Checks should cover missing accounts/categories/payment methods, duplicates, invalid currencies, unsupported conversions, zero values, future dates, loan-link gaps, over-repayments, orphaned records, partial import failures and unreconciled balances."),
                "Run Data Check",
                "Create Quality Request",
                false
        );
    }

    private PolicySpec auditSpec(String area) {
        return spec(
                area,
                "Protect the audit trail for financial, authentication, administrative, sync and disposal events.",
                lines("Action -> Immutable audit entry -> Review/export -> Retain under policy. Audit events are never edited from the user interface."),
                lines("View Audit", "Filter By User", "Filter By Module", "Export Audit", "View Related Record", "View Approval Reference"),
                lines("Super Administrators may view complete audit. Administrators see limited scope. Users see own activity where appropriate. Nobody edits audit events."),
                lines("Required fields include audit ID, timestamp, user, role, workspace, session, module, entity type, entity ID, action, old value, new value, reason, approval reference, device, app version, result, error and related record."),
                "Load Audit Evidence",
                "Create Audit Export Request",
                false
        );
    }

    private PolicySpec syncSpec(String area) {
        return spec(
                area,
                "Define local-to-server synchronisation rules before any future remote sync writes to central storage.",
                lines("Local change -> Queue -> Validate -> Push via authenticated API -> Resolve conflict/quarantine -> Server acknowledgement -> Local confirmation."),
                lines("View Pending Queue", "Retry Failed Records", "Resolve Conflict", "Quarantine Record", "Escalate", "Export Sync History"),
                lines("Future server sync should use JavaFX Desktop -> Authenticated API -> Central PostgreSQL, not a direct desktop-to-database connection."),
                lines("Hard deletion must sync through tombstones containing record ID, deletion status, deleted by, deleted at, reason, approval ID, source device and server acknowledgement."),
                "Review Sync Rules",
                "Create Sync Request",
                false
        );
    }

    private PolicySpec maintenanceSpec(String area) {
        return switch (area) {
            case "Clear Test or Demo Data" -> spec(
                    area,
                    "Clear only records explicitly marked TEST, DEMO, SAMPLE or TRAINING after preview, backup and verification.",
                    lines("Scan explicit test flags -> Preview impact -> Backup now -> Clear controlled scope -> Verify database -> Record maintenance history."),
                    lines("Scan Test Data", "Preview Impact", "Backup Now", "Clear Test Data", "Verify Database", "Export Disposal Report"),
                    lines("Visible only to Super Administrators. Date ranges or descriptions alone must not be used to identify test data."),
                    lines("Evidence must include test flag, affected module, record count, financial total, backup reference, executor and verification result."),
                    "Preview Maintenance Impact",
                    "Record Disposal Request",
                    true
            );
            case "Purge Archived Records" -> spec(
                    area,
                    "Purge archived records only after retention, dependency, reporting and backup controls are satisfied.",
                    lines("Select archived period -> Confirm retention expiry -> Check dependencies -> Verify reports preserved -> Backup -> Purge controlled scope -> Recalculate -> Audit certificate."),
                    lines("Preview Records", "Check Dependencies", "Backup Now", "Approve Disposal", "Purge Selected", "Export Disposal Report"),
                    lines("Only a Super Administrator may purge archived records. Archived financial evidence should remain available for preserved reports."),
                    lines("Evidence must include retention basis, dependencies, preserved report references, backup checksum and disposal certificate."),
                    "Preview Maintenance Impact",
                    "Record Disposal Request",
                    true
            );
            case "Reset Workspace" -> spec(
                    area,
                    "Reset a workspace through explicit scope options that state what will remain after execution.",
                    lines("Choose reset option -> Dependency analysis -> Affected count/totals -> Mandatory reason -> Backup -> Password -> Confirmation phrase -> Maintenance mode -> Transaction -> Verify -> Certificate."),
                    lines("Preview Impact", "Check Dependencies", "Backup Now", "Reset Workspace", "Verify Database", "Export Disposal Report"),
                    lines("Only a Super Administrator may reset a workspace. Login identity and workspace data deletion are separate policy decisions."),
                    lines("Evidence must state whether login, configuration and financial records were retained, plus backup reference and verification result."),
                    "Preview Maintenance Impact",
                    "Record Disposal Request",
                    true
            );
            case "Delete Workspace" -> spec(
                    area,
                    "Delete the complete user financial workspace without automatically deleting the central user identity.",
                    lines("Select workspace -> Dependency analysis -> Affected totals -> Backup -> Super Admin password -> Type DELETE WORKSPACE scope phrase -> Maintenance mode -> Delete workspace -> Verify -> Certificate."),
                    lines("Preview Impact", "Check Dependencies", "Backup Now", "Delete Workspace", "Verify Database", "Export Disposal Report"),
                    lines("Only a Super Administrator may delete a workspace. Normal users and Administrators must not self-service physical workspace deletion."),
                    lines("Evidence must include workspace ID, owner, affected modules, financial total, backup reference, confirmation phrase and immutable disposal audit."),
                    "Preview Maintenance Impact",
                    "Record Disposal Request",
                    true
            );
            case "Maintenance History" -> spec(
                    area,
                    "Review controlled maintenance operations, approvals, backups, execution status and verification evidence.",
                    lines("Operation requested -> Approved -> Backup verified -> Executed -> Recalculated -> Verified -> Certificate retained."),
                    lines("View Operation", "Filter By Scope", "View Backup Reference", "View Verification Result", "Export Maintenance History"),
                    lines("Super Administrators may view full maintenance history. Audit evidence remains immutable and is not edited from this page."),
                    lines("Required columns: operation, scope, records affected, financial amount, requested by, approved by, executed by, backup reference, start time, completion time, verification result and reason."),
                    "Load Maintenance History",
                    "Create Audit Export Request",
                    false
            );
            default -> spec(
                    area,
                    "Super Administrator-only area for disposal, purge, workspace reset and workspace deletion controls.",
                    lines("Select scope -> Dependency analysis -> Affected count/totals -> Mandatory reason -> Recovery backup -> Re-authenticate -> Confirmation phrase -> Maintenance mode -> Transactional execution -> Recalculate -> Verify -> Immutable disposal audit -> Disposal certificate."),
                    lines("Preview Records", "Check Dependencies", "Create Deletion Request", "Approve Disposal", "Purge Selected", "Export Disposal Report"),
                    lines("Visible only to Super Administrators. Administrators and normal users must not physically delete, purge, clear or reset financial data."),
                    lines("Physical disposal must preserve audit summary and backup reference. Posted records should normally be cancelled, reversed, frozen or archived instead of deleted."),
                    "Preview Maintenance Impact",
                    "Record Disposal Request",
                    true
            );
        };
    }

    private void renderWorkflowSteps(String lifecycle) {
        if (workflowStepsPane == null) {
            return;
        }
        workflowStepsPane.getChildren().clear();
        List<String> steps = workflowSteps(lifecycle);
        for (int index = 0; index < steps.size(); index++) {
            VBox card = new VBox(5);
            card.getStyleClass().add("workflow-step-card");
            card.setPrefWidth(155);
            Label number = new Label(String.format(Locale.ENGLISH, "%02d", index + 1));
            number.getStyleClass().add("workflow-step-number");
            Label label = new Label(steps.get(index));
            label.setWrapText(true);
            label.getStyleClass().add("workflow-step-label");
            card.getChildren().setAll(number, label);
            workflowStepsPane.getChildren().add(card);
        }
    }

    private void renderWireframeFields(String area) {
        if (wireframeFieldsPane == null) {
            return;
        }
        wireframeFieldsPane.getChildren().clear();
        for (WireframeField field : wireframeFieldsFor(area)) {
            VBox card = new VBox(4);
            card.getStyleClass().add("wireframe-field-card");
            card.setPrefWidth(205);
            Label name = new Label(field.name());
            name.setWrapText(true);
            name.getStyleClass().add("field-label");
            Label purpose = new Label(field.purpose());
            purpose.setWrapText(true);
            purpose.getStyleClass().add("muted-label");
            card.getChildren().setAll(name, purpose);
            wireframeFieldsPane.getChildren().add(card);
        }
    }

    private void renderProcessButtons(String controls) {
        if (processActionsPane == null) {
            return;
        }
        processActionsPane.getChildren().clear();
        for (String action : controlActions(controls)) {
            Button button = new Button(action);
            button.setWrapText(true);
            button.setMinWidth(action.length() > 18 ? 185 : 145);
            button.setMaxWidth(225);
            button.getStyleClass().add("process-action-button");
            if (requiresControlledWorkflow(action)) {
                button.getStyleClass().add("danger-button");
            } else if (isPrimaryProcessAction(action)) {
                button.getStyleClass().add("primary-button");
            } else {
                button.getStyleClass().add("secondary-button");
            }
            button.setOnAction(event -> runProcessAction(action));
            processActionsPane.getChildren().add(button);
        }
    }

    private List<String> workflowSteps(String lifecycle) {
        String cleanLifecycle = lifecycle == null ? "" : lifecycle.trim();
        if (cleanLifecycle.isBlank()) {
            return List.of("Select process", "Review controls", "Record evidence");
        }
        String firstLine = cleanLifecycle.lines().findFirst().orElse(cleanLifecycle);
        String source = firstLine.contains("->") ? firstLine : cleanLifecycle;
        return source.lines()
                .flatMap(line -> List.of(line.split("\\s*->\\s*|\\s*/\\s*")).stream())
                .map(this::cleanStep)
                .filter(value -> !value.isBlank())
                .limit(12)
                .toList();
    }

    private String cleanStep(String value) {
        return value == null ? "" : value.replace(".", "").trim();
    }

    private List<String> controlActions(String controls) {
        if (controls == null || controls.isBlank()) {
            return List.of("Review Process");
        }
        return controls.lines()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private List<WireframeField> wireframeFieldsFor(String area) {
        if (isImportArea(area)) {
            return List.of(
                    field("Import Type", "Bank, mobile-money, CSV, budget, goal, loan or setup record."),
                    field("Source File", "Original filename and uploaded location."),
                    field("File Checksum", "Duplicate batch and tamper detection."),
                    field("Mapping Profile", "Column-to-field mapping selected by the user."),
                    field("Validation Status", "Draft, valid, rejected, duplicate or quarantined."),
                    field("Financial Effect", "Preview of balances, reports and records affected."),
                    field("Approval Status", "Reviewer, decision, reason and date."),
                    field("Batch ID", "Links committed rows back to import evidence.")
            );
        }
        if (isRecordsControlArea(area)) {
            return List.of(
                    field("Module", "Account, transaction, budget, project, goal, loan, contact or report input."),
                    field("Record ID", "Stable identifier for history and dependency checks."),
                    field("Lifecycle Status", "Draft, posted, frozen, cancelled, reversed, archived, pending purge or purged."),
                    field("Requested Action", "Freeze, unfreeze request, cancellation request, reversal request or archive."),
                    field("Reason", "Mandatory business explanation for lifecycle changes."),
                    field("Affected Reports", "Issued or draft reports that rely on the record."),
                    field("Financial Effect", "Whether totals remain included, excluded or neutralised."),
                    field("Approval Reference", "Super Administrator or authorised approval evidence.")
            );
        }
        if (isDataQualityArea(area)) {
            return List.of(
                    field("Check Type", "Missing data, duplicate, reconciliation, relationship or exception check."),
                    field("Severity", "Critical, high, medium or low data-risk classification."),
                    field("Affected Record", "Module and record ID requiring review."),
                    field("Expected Value", "System-calculated or source-supported value."),
                    field("Actual Value", "Stored value or imported value under review."),
                    field("Difference", "Amount or condition that must be resolved."),
                    field("Resolution Status", "Open, assigned, corrected, accepted or closed."),
                    field("Evidence Link", "Audit, import, source document or reconciliation reference.")
            );
        }
        if (isAuditArea(area)) {
            return List.of(
                    field("Audit ID", "Immutable event identifier."),
                    field("Timestamp", "Date and time the event occurred."),
                    field("User / Role", "Actor and permission context."),
                    field("Module", "Functional area where the event happened."),
                    field("Entity Type / ID", "Record affected by the action."),
                    field("Action", "Create, update, freeze, cancel, reverse, archive, export or disposal."),
                    field("Old / New Value", "Before-and-after evidence where applicable."),
                    field("Result", "Success, warning, failure or blocked action.")
            );
        }
        if (isSyncArea(area)) {
            return List.of(
                    field("Entity", "Record type and local identifier."),
                    field("Operation", "Create, update, tombstone, retry, quarantine or resolve."),
                    field("Local Version", "Device-side version and timestamp."),
                    field("Central Version", "Server-side version when remote sync exists."),
                    field("Conflict Status", "Pending, assigned, resolved, quarantined or acknowledged."),
                    field("Retry Count", "Number of failed delivery attempts."),
                    field("Resolution", "Chosen source of truth and approver."),
                    field("Server Acknowledgement", "Central confirmation timestamp for accepted changes.")
            );
        }
        if ("Maintenance History".equals(area)) {
            return List.of(
                    field("Operation", "Disposal, purge, clear test data, reset or workspace deletion."),
                    field("Scope", "Module, date range, workspace, account, project, batch or record ID."),
                    field("Records Affected", "Count of rows included in the operation."),
                    field("Financial Amount", "Total financial value affected by the operation."),
                    field("Requested / Approved / Executed By", "People responsible for each control point."),
                    field("Backup Reference", "Verified recovery artifact and checksum."),
                    field("Verification Result", "Integrity, reconciliation and recalculation outcome."),
                    field("Reason", "Mandatory disposal or maintenance justification.")
            );
        }
        if (isMaintenanceArea(area)) {
            return List.of(
                    field("Disposal Type", "Record disposal, clear test data, purge, reset or workspace deletion."),
                    field("Scope", "Module, status, account, project, user, import batch, archived period or record ID."),
                    field("Dependency Result", "References found in transactions, reports, audit, imports or sync queues."),
                    field("Record Count", "Affected rows before execution."),
                    field("Financial Total", "Affected balances or amounts before execution."),
                    field("Backup Reference", "Recovery backup created and verified before execution."),
                    field("Confirmation Phrase", "Specific operation and scope phrase typed by Super Administrator."),
                    field("Verification Result", "Post-action integrity, reconciliation and certificate result.")
            );
        }
        return List.of(
                field("Entity Type", "Financial, setup, import, sync or report-input record."),
                field("Lifecycle Status", "Current stage in the controlled record lifecycle."),
                field("Owner", "User or role responsible for the next process step."),
                field("Reason", "Business justification for any controlled change."),
                field("Evidence", "Source document, audit event, approval or backup reference."),
                field("Outcome", "Preview, request, approval, verification or immutable history entry.")
        );
    }

    private WireframeField field(String name, String purpose) {
        return new WireframeField(name, purpose);
    }

    private String phaseFor(String area) {
        if (isMaintenanceArea(area)) {
            return "Restricted disposal";
        }
        if (isSyncArea(area)) {
            return "Synchronisation / recovery";
        }
        if (isAuditArea(area)) {
            return "Evidence and retention";
        }
        if (isDataQualityArea(area)) {
            return "Integrity assurance";
        }
        if (isRecordsControlArea(area)) {
            return "Record lifecycle";
        }
        if (isImportArea(area)) {
            return "Data capture / posting";
        }
        return "Governance";
    }

    private String ownerFor(String area) {
        if (isMaintenanceArea(area)) {
            return "Super Administrator";
        }
        if (isAuditArea(area)) {
            return "Super Administrator / Auditor";
        }
        if (isSyncArea(area)) {
            return "System Administrator";
        }
        if (isDataQualityArea(area)) {
            return "Administrator / Reviewer";
        }
        if (isRecordsControlArea(area)) {
            return "Administrator with Super Admin approval";
        }
        if (isImportArea(area)) {
            return "Administrator / Approval Role";
        }
        return "Governance owner";
    }

    private String riskFor(String area) {
        if (isMaintenanceArea(area)) {
            return "Critical";
        }
        if (isDataQualityArea(area) || isAuditArea(area) || isSyncArea(area) || isImportArea(area)) {
            return "High";
        }
        if (isRecordsControlArea(area)) {
            return "High for posted records";
        }
        return "Controlled";
    }

    private String impactFor(String area) {
        if (isMaintenanceArea(area)) {
            return "Can physically affect workspace data only after Super Administrator controls, backup and verification.";
        }
        if (isSyncArea(area)) {
            return "No remote write from this screen; conflicts are previewed, requested and audited.";
        }
        if (isAuditArea(area)) {
            return "Read-only evidence. Audit records are not edited from the user interface.";
        }
        if (isDataQualityArea(area)) {
            return "Read-only checks; corrections move through controlled record lifecycle requests.";
        }
        if (isRecordsControlArea(area)) {
            return "Freeze keeps valid records in calculations; cancellation or reversal changes reporting effect through approval.";
        }
        if (isImportArea(area)) {
            return "Imports affect balances only after validation, duplicate review, approval and commit controls.";
        }
        return "No direct financial posting from this page.";
    }

    private boolean isPrimaryProcessAction(String action) {
        String lower = action.toLowerCase(Locale.ENGLISH);
        return lower.contains("preview")
                || lower.contains("validate")
                || lower.contains("check")
                || lower.contains("review")
                || lower.contains("view")
                || lower.contains("run")
                || lower.contains("scan")
                || lower.contains("verify");
    }

    private boolean isBackupAction(String action) {
        return action.toLowerCase(Locale.ENGLISH).contains("backup");
    }

    private boolean isExportAction(String action) {
        String lower = action.toLowerCase(Locale.ENGLISH);
        return lower.contains("export") || lower.contains("download");
    }

    private boolean isDataQualityAction(String action) {
        String lower = action.toLowerCase(Locale.ENGLISH);
        return lower.contains("duplicate")
                || lower.contains("reconcile")
                || lower.contains("data check")
                || lower.contains("exceptions")
                || lower.contains("missing")
                || lower.contains("validate")
                || lower.contains("verify database");
    }

    private boolean requiresControlledWorkflow(String action) {
        String lower = action.toLowerCase(Locale.ENGLISH).trim();
        if (lower.startsWith("view ")
                || lower.startsWith("filter ")
                || lower.startsWith("preview ")
                || lower.startsWith("load ")
                || lower.startsWith("run ")
                || lower.startsWith("scan ")
                || lower.startsWith("verify ")
                || lower.startsWith("check ")
                || lower.startsWith("download ")
                || lower.startsWith("export ")
                || lower.startsWith("select ")
                || lower.startsWith("map ")) {
            return false;
        }
        return lower.startsWith("commit ")
                || lower.startsWith("post ")
                || lower.startsWith("submit ")
                || lower.startsWith("approve ")
                || lower.startsWith("reject ")
                || lower.startsWith("rollback ")
                || lower.startsWith("roll back ")
                || lower.startsWith("resolve ")
                || lower.startsWith("freeze ")
                || lower.startsWith("request ")
                || lower.startsWith("create ")
                || lower.startsWith("correct ")
                || lower.startsWith("clear ")
                || lower.startsWith("purge ")
                || lower.startsWith("reset ")
                || lower.startsWith("delete ")
                || lower.startsWith("retry ")
                || lower.startsWith("quarantine ")
                || lower.startsWith("escalate ")
                || lower.startsWith("cancel ");
    }

    private void prepareControlledWorkflow(String action) {
        boolean maintenance = isMaintenanceArea(currentArea);
        if (maintenance && !UserSession.isSuperAdmin()) {
            UiAlerts.info("Only a Super Administrator may approve or execute Data Maintenance controls.");
            return;
        }
        StringBuilder builder = new StringBuilder(actionHeader(action));
        builder.append("Controlled workflow prepared. No financial record was changed from this page.")
                .append(System.lineSeparator())
                .append(System.lineSeparator());
        if (maintenance) {
            builder.append("Mandatory Super Administrator controls:")
                    .append(System.lineSeparator())
                    .append("1. Select disposal type and exact scope")
                    .append(System.lineSeparator())
                    .append("2. Run dependency analysis across accounts, transactions, reports, imports, audit and sync queues")
                    .append(System.lineSeparator())
                    .append("3. Display affected record count and financial totals")
                    .append(System.lineSeparator())
                    .append("4. Capture mandatory reason and approval evidence")
                    .append(System.lineSeparator())
                    .append("5. Create and verify recovery backup")
                    .append(System.lineSeparator())
                    .append("6. Re-authenticate and type the operation-specific confirmation phrase")
                    .append(System.lineSeparator())
                    .append("7. Execute in maintenance mode inside a database transaction")
                    .append(System.lineSeparator())
                    .append("8. Recalculate balances, verify integrity and write immutable disposal audit.");
        } else {
            builder.append("Required process controls:")
                    .append(System.lineSeparator())
                    .append("1. Select affected record or batch")
                    .append(System.lineSeparator())
                    .append("2. Capture reason and source evidence")
                    .append(System.lineSeparator())
                    .append("3. Preview affected reports, balances and related records")
                    .append(System.lineSeparator())
                    .append("4. Route to authorised approval role where required")
                    .append(System.lineSeparator())
                    .append("5. Preserve the original record and create status history")
                    .append(System.lineSeparator())
                    .append("6. Notify the Super Administrator for sensitive lifecycle changes.");
        }
        resultArea.setText(builder.toString());
        policyStatusLabel.setText("Controlled request prepared. No data was changed.");
        logProcessAction(action, maintenance ? "WARNING" : "INFO", "Action routed through controlled workflow; no data changed.");
    }

    private String exportProcessEvidence(String action) {
        try {
            Path exportDirectory = DatabaseHandler.applicationDataDirectory()
                    .resolve("exports")
                    .resolve("data-records")
                    .toAbsolutePath()
                    .normalize();
            Files.createDirectories(exportDirectory);
            Path exportFile = exportDirectory.resolve(slug(currentArea) + "-" + slug(action) + "-"
                    + LocalDateTime.now().format(EXPORT_TIMESTAMP_FORMAT) + ".txt");
            Files.writeString(exportFile, exportBody(action), StandardCharsets.UTF_8);
            logProcessAction(action, "INFO", "Process evidence exported to " + exportFile);
            return "Process evidence exported:" + System.lineSeparator()
                    + exportFile + System.lineSeparator()
                    + System.lineSeparator()
                    + "The export is governance evidence only. It does not post, delete, purge, reset or change records.";
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to export Data and Records process evidence", exception);
        }
    }

    private String exportBody(String action) {
        return lines(
                "Data and Records Process Evidence",
                "Generated At: " + LocalDateTime.now(),
                "Section: " + currentArea,
                "Action: " + action,
                "",
                "Phase: " + phaseFor(currentArea),
                "Owner: " + ownerFor(currentArea),
                "Risk: " + riskFor(currentArea),
                "Impact: " + impactFor(currentArea),
                "",
                "Workflow:",
                String.join(" -> ", workflowSteps(lifecycleArea == null ? "" : lifecycleArea.getText())),
                "",
                "Controls:",
                controlsArea == null ? "" : controlsArea.getText(),
                "",
                "Permissions:",
                permissionsArea == null ? "" : permissionsArea.getText(),
                "",
                "Evidence Requirements:",
                evidenceArea == null ? "" : evidenceArea.getText()
        );
    }

    private String actionHeader(String action) {
        return "Process action: " + action + System.lineSeparator()
                + "Section: " + currentArea + System.lineSeparator()
                + "Timestamp: " + LocalDateTime.now() + System.lineSeparator()
                + System.lineSeparator();
    }

    private String processWireframeNarrative(String action) {
        return System.lineSeparator()
                + System.lineSeparator()
                + "Wireframe expectation for \"" + action + "\":" + System.lineSeparator()
                + "- Capture the minimum fields shown in the wireframe cards" + System.lineSeparator()
                + "- Show lifecycle status, owner, risk and financial impact before confirmation" + System.lineSeparator()
                + "- Link every decision to source evidence, approval reference and audit history" + System.lineSeparator()
                + "- Keep posted financial records immutable; use cancellation or reversal when financial effect must change";
    }

    private String slug(String value) {
        String slug = value == null ? "" : value.toLowerCase(Locale.ENGLISH)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isBlank() ? "data-records" : slug;
    }

    private void logProcessAction(String action, String severity, String message) {
        try {
            database.recordSystemLog("Data And Records", currentArea + " - " + action, severity, message);
        } catch (RuntimeException ignored) {
            // The UI action remains useful even if audit persistence is temporarily unavailable.
        }
    }

    private PolicySpec spec(
            String title,
            String description,
            String lifecycle,
            String controls,
            String permissions,
            String evidence,
            String primaryAction,
            String secondaryAction,
            boolean backupVisible
    ) {
        return new PolicySpec(
                title,
                description,
                lifecycle,
                controls,
                permissions,
                evidence,
                primaryAction,
                secondaryAction,
                backupVisible,
                "Select an action to review evidence. No records are changed from this policy page."
        );
    }

    private String maintenancePreview() {
        return "Data Maintenance impact preview\n\n"
                + database.maintenanceSummary()
                + "\n\nRequired before execution:\n"
                + "- Dependency analysis across transactions, accounts, reports, audit events, imports and sync queues\n"
                + "- Verified recovery backup\n"
                + "- Super Administrator password re-entry\n"
                + "- Specific confirmation phrase that includes operation and scope\n"
                + "- Database transaction, recalculation, integrity check and immutable disposal audit";
    }

    private String importWorkflow() {
        return lines(
                "Import workflow preview",
                "",
                "1. Select file",
                "2. Select import type",
                "3. Map columns",
                "4. Validate format",
                "5. Detect duplicates",
                "6. Preview financial effect",
                "7. Correct or reject invalid rows",
                "8. Approve import",
                "9. Commit valid records",
                "10. Reconcile totals",
                "11. Record import audit"
        );
    }

    private String syncRules() {
        return lines(
                "Recommended sync architecture",
                "",
                "JavaFX Desktop -> Authenticated API -> Central PostgreSQL",
                "",
                "Do not connect the desktop client directly to the central PostgreSQL database.",
                "Deletion sync must use tombstones and wait for central acknowledgement before local purge."
        );
    }

    private String recordLifecycleEvidence() {
        return lines(
                "Record lifecycle matrix",
                "",
                "Draft: not final, excluded from reports, editable.",
                "Posted: valid financial record, included in reports, limited editing.",
                "Frozen: valid but locked, included in reports, not editable.",
                "Cancelled: invalidated record, excluded from operational totals.",
                "Reversed: original and reversal shown, net effect neutralised.",
                "Archived: historical record, hidden from normal operations.",
                "Pending purge: awaiting Super Administrator disposal.",
                "Purged: physically removed after formal retention and audit controls."
        );
    }

    private String historySummary(String heading) {
        List<SystemLogRecord> records = database.listSystemLogHistory(30);
        StringBuilder builder = new StringBuilder(heading).append("\n\n");
        if (records.isEmpty()) {
            builder.append("No recent system events found.");
            return builder.toString();
        }
        for (SystemLogRecord record : records) {
            builder.append(record.getCreatedAt())
                    .append(" | ")
                    .append(record.getModuleName())
                    .append(" | ")
                    .append(record.getActionName())
                    .append(" | ")
                    .append(record.getSeverity())
                    .append('\n');
        }
        return builder.toString();
    }

    private boolean isImportArea(String area) {
        return area != null && (area.contains("Import") || "File Import".equals(area) || "Posting and Approval".equals(area) || "Rejected Records".equals(area));
    }

    private boolean isRecordsControlArea(String area) {
        return List.of("Active Records", "Draft Records", "Frozen Records", "Cancelled and Reversed", "Archived Records", "Correction Requests").contains(area);
    }

    private boolean isDataQualityArea(String area) {
        return List.of("Data Health Overview", "Missing Information", "Duplicate Records", "Account Reconciliation", "Relationship Errors", "Exceptions").contains(area);
    }

    private boolean isAuditArea(String area) {
        return List.of("Financial Record History", "Administrative Actions", "Data Disposal History", "Audit Export").contains(area);
    }

    private boolean isSyncArea(String area) {
        return List.of("Pending Queue", "Failed Records", "Conflicts", "Quarantine", "Sync History").contains(area);
    }

    private boolean isMaintenanceArea(String area) {
        return List.of("Record Disposal", "Clear Test or Demo Data", "Purge Archived Records", "Reset Workspace", "Delete Workspace", "Maintenance History").contains(area);
    }

    private String lines(String... values) {
        return String.join(System.lineSeparator(), values);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null || current.getMessage().isBlank()
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }

    private record PolicySpec(
            String title,
            String description,
            String lifecycle,
            String controls,
            String permissions,
            String evidence,
            String primaryAction,
            String secondaryAction,
            boolean backupVisible,
            String initialResult
    ) {
    }

    private record WireframeField(String name, String purpose) {
    }
}
