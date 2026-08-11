package com.wk.pfmis.controllers;

import com.wk.pfmis.ai.AiRecommendationService;
import com.wk.pfmis.ai.BundledLocalAiManager;
import com.wk.pfmis.db.DatabaseHandler;
import com.wk.pfmis.models.AiSettings;
import com.wk.pfmis.utils.ExportPathService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class SettingsController {
    @FXML private RadioButton builtInLocalAiRadio;
    @FXML private RadioButton customAiProviderRadio;
    @FXML private VBox builtInLocalAiPane;
    @FXML private VBox customAiProviderPane;
    @FXML private ComboBox<String> providerBox;
    @FXML private TextField endpointField;
    @FXML private ComboBox<String> modelBox;
    @FXML private PasswordField apiKeyField;
    @FXML private Label registrationStatusLabel;
    @FXML private Label apiKeyStatusLabel;
    @FXML private Label localAiStatusLabel;
    @FXML private Label downloadStatusLabel;

    @FXML private CheckBox pfmisCopilotAgentBox;
    @FXML private CheckBox transactionCoachAgentBox;
    @FXML private CheckBox dataQualityGuardianAgentBox;
    @FXML private CheckBox goalCoachAgentBox;
    @FXML private CheckBox budgetAnalystAgentBox;
    @FXML private CheckBox loanAdvisorAgentBox;
    @FXML private CheckBox projectAdvisorAgentBox;
    @FXML private CheckBox newUserAgentBox;
    @FXML private CheckBox backupGuardianAgentBox;

    @FXML private CheckBox openAiConnectorExtensionBox;
    @FXML private CheckBox openRouterConnectorExtensionBox;
    @FXML private CheckBox geminiConnectorExtensionBox;
    @FXML private CheckBox claudeConnectorExtensionBox;
    @FXML private CheckBox cohereConnectorExtensionBox;
    @FXML private CheckBox bundledLocalExtensionBox;
    @FXML private CheckBox localAiExtensionBox;
    @FXML private CheckBox csvInsightExtensionBox;
    @FXML private CheckBox receiptTemplateExtensionBox;
    @FXML private CheckBox backupGuideExtensionBox;

    private final DatabaseHandler database = DatabaseHandler.getInstance();
    private final AiRecommendationService aiService = new AiRecommendationService();

    @FXML
    public void initialize() {
        ToggleGroup aiModeGroup = new ToggleGroup();
        builtInLocalAiRadio.setToggleGroup(aiModeGroup);
        customAiProviderRadio.setToggleGroup(aiModeGroup);
        builtInLocalAiRadio.selectedProperty().addListener((observable, oldValue, newValue) -> applyBundledLocalUi());
        customAiProviderRadio.selectedProperty().addListener((observable, oldValue, newValue) -> applyBundledLocalUi());
        providerBox.setItems(FXCollections.observableArrayList(
                AiSettings.PROVIDER_OPENAI_COMPATIBLE,
                AiSettings.PROVIDER_OPENAI,
                AiSettings.PROVIDER_OPENROUTER,
                AiSettings.PROVIDER_GROQ,
                AiSettings.PROVIDER_DEEPSEEK,
                AiSettings.PROVIDER_MISTRAL,
                AiSettings.PROVIDER_ANTHROPIC,
                AiSettings.PROVIDER_GEMINI,
                AiSettings.PROVIDER_COHERE,
                AiSettings.PROVIDER_OLLAMA,
                AiSettings.PROVIDER_CUSTOM
        ));
        modelBox.setEditable(true);
        providerBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            refreshModelChoices(oldValue, newValue);
            applyProviderDefaults(oldValue, newValue);
            applyBundledLocalUi();
        });
        loadSettings();
        refreshLocalAiStatus();
    }

    @FXML
    private void saveAiRegistration() {
        try {
            AiSettings settings = formSettings(true);
            database.saveAiSettings(settings);
            apiKeyField.clear();
            loadSettings();
            UiAlerts.info(settings.isBundledLocalProvider() ? "PFMIS Local AI restored." : "Smart Analysis configuration saved.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to save Smart Analysis configuration", exception);
        }
    }

    @FXML
    private void clearApiKey() {
        try {
            AiSettings current = formSettings(false);
            if (current.isBundledLocalProvider()) {
                database.saveAiSettings(defaultLocalSettings());
                apiKeyField.clear();
                loadSettings();
                UiAlerts.info("PFMIS Local AI does not use an API key.");
                return;
            }
            AiSettings settings = new AiSettings(
                    current.isEnabled(),
                    current.getDisplayName(),
                    current.getProvider(),
                    current.getEndpoint(),
                    current.getModel(),
                    "",
                    current.getAgents(),
                    current.getExtensions(),
                    AiSettings.KEY_STATUS_INACTIVE,
                    false
            );
            database.saveAiSettings(settings);
            apiKeyField.clear();
            loadSettings();
            UiAlerts.info("Provider API key cleared.");
        } catch (RuntimeException exception) {
            UiAlerts.error("Failed to clear provider API key", exception);
        }
    }

    @FXML
    private void testAiRegistration() {
        AiSettings settings;
        try {
            settings = formSettings(true);
        } catch (RuntimeException exception) {
            UiAlerts.error("Smart Analysis registration is not ready", exception);
            return;
        }
        if (settings.isBundledLocalProvider()) {
            localAiStatusLabel.setText("Status: Starting");
            registrationStatusLabel.setText("Starting and testing PFMIS Local AI...");
        } else {
            registrationStatusLabel.setText("Testing provider connection...");
        }
        boolean testingSavedConfiguration = isSavedAiConfiguration(settings);
        CompletableFuture.supplyAsync(() -> aiService.testConnection(settings))
                .whenComplete((message, throwable) -> Platform.runLater(() -> {
                    if (throwable == null) {
                        if (testingSavedConfiguration) {
                            database.saveAiKeyStatus(AiSettings.KEY_STATUS_ACTIVE);
                            apiKeyStatusLabel.setText(database.getAiSettings().maskedApiKey());
                            registrationStatusLabel.setText("Smart Analysis active. Test passed: " + message);
                        } else {
                            apiKeyStatusLabel.setText(settings.maskedApiKey());
                            registrationStatusLabel.setText("Connection test passed. Save Configuration or Restore PFMIS Local AI to make it active.");
                        }
                        refreshLocalAiStatus();
                    } else {
                        String errorMessage = rootMessage(throwable);
                        if (testingSavedConfiguration) {
                            if (isInactiveKeyError(errorMessage)) {
                                database.saveAiKeyStatus(AiSettings.KEY_STATUS_INACTIVE);
                            } else if (isQuotaOrRateLimitError(errorMessage)) {
                                database.saveAiKeyStatus(AiSettings.KEY_STATUS_QUOTA_LIMITED);
                            }
                            apiKeyStatusLabel.setText(database.getAiSettings().maskedApiKey());
                        } else {
                            apiKeyStatusLabel.setText(settings.maskedApiKey());
                        }
                        registrationStatusLabel.setText(errorMessage);
                        refreshLocalAiStatus();
                    }
                }));
    }

    @FXML
    private void loadProviderModels() {
        if (builtInLocalAiRadio.isSelected()) {
            modelBox.setItems(FXCollections.observableArrayList(AiSettings.BUNDLED_LOCAL_MODEL));
            modelBox.setValue(AiSettings.BUNDLED_LOCAL_MODEL);
            registrationStatusLabel.setText("PFMIS Local AI uses the bundled model.");
            return;
        }
        AiSettings settings;
        try {
            settings = formSettings(true);
        } catch (RuntimeException exception) {
            UiAlerts.error("Smart Analysis registration is not ready", exception);
            return;
        }
        registrationStatusLabel.setText("Loading models from " + settings.getProvider() + "...");
        String currentModel = comboText(modelBox);
        CompletableFuture.supplyAsync(() -> aiService.listModels(settings))
                .whenComplete((models, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        registrationStatusLabel.setText("Could not load provider models: " + rootMessage(throwable));
                        return;
                    }
                    if (models == null || models.isEmpty()) {
                        registrationStatusLabel.setText("No models were returned by " + settings.getProvider() + ".");
                        return;
                    }
                    modelBox.setItems(FXCollections.observableArrayList(models));
                    if (!currentModel.isBlank()) {
                        modelBox.setValue(currentModel);
                        modelBox.getEditor().setText(currentModel);
                    } else {
                        modelBox.setValue(models.getFirst());
                        modelBox.getEditor().setText(models.getFirst());
                    }
                    registrationStatusLabel.setText("Loaded " + models.size() + " model(s) from " + settings.getProvider() + ".");
                }));
    }

    @FXML
    private void restartLocalAi() {
        database.saveAiSettings(defaultLocalSettings());
        localAiStatusLabel.setText("Status: Starting");
        registrationStatusLabel.setText("Restarting PFMIS Local AI...");
        CompletableFuture.supplyAsync(BundledLocalAiManager::restart)
                .whenComplete((message, throwable) -> Platform.runLater(() -> {
                    if (throwable == null) {
                        registrationStatusLabel.setText(message);
                    } else {
                        registrationStatusLabel.setText(rootMessage(throwable));
                    }
                    loadSettings();
                    refreshLocalAiStatus();
                }));
    }

    @FXML
    private void openAiLogs() {
        try {
            Files.createDirectories(BundledLocalAiManager.logDirectory());
            if (!Desktop.isDesktopSupported()) {
                UiAlerts.info("Smart Analysis logs are here: " + BundledLocalAiManager.logDirectory());
                return;
            }
            Desktop.getDesktop().open(BundledLocalAiManager.logDirectory().toFile());
        } catch (Exception exception) {
            UiAlerts.error("Failed to open Smart Analysis logs", exception);
        }
    }

    @FXML
    private void restorePfmisLocalAi() {
        database.saveAiSettings(defaultLocalSettings());
        apiKeyField.clear();
        loadSettings();
        refreshLocalAiStatus();
        UiAlerts.info("PFMIS Local AI is now active.");
    }

    @FXML
    private void downloadSelectedAiItems() {
        try {
            List<StarterItem> selectedItems = selectedStarterItems();
            if (selectedItems.isEmpty()) {
                UiAlerts.info("Select at least one profile or extension.");
                return;
            }
            Path directory = ExportPathService.resolveExportDirectory(
                    ExportPathService.defaultDirectoryName("AI Starter Pack")
            );
            Files.writeString(directory.resolve("README.txt"), starterReadme(selectedItems), StandardCharsets.UTF_8);
            for (StarterItem item : selectedItems) {
                Files.writeString(directory.resolve(item.fileName()), item.content(), StandardCharsets.UTF_8);
            }
            downloadStatusLabel.setText("Downloaded " + selectedItems.size() + " item(s)." + System.lineSeparator()
                    + System.lineSeparator()
                    + "Saved to:" + System.lineSeparator()
                    + directory);
        } catch (Exception exception) {
            UiAlerts.error("Failed to download Smart Analysis starter pack", exception);
        }
    }

    private void loadSettings() {
        AiSettings settings = database.getAiSettings();
        boolean builtInLocal = settings.isBundledLocalProvider();
        builtInLocalAiRadio.setSelected(builtInLocal);
        customAiProviderRadio.setSelected(!builtInLocal);
        String provider = builtInLocal ? AiSettings.PROVIDER_OPENAI_COMPATIBLE : settings.getProvider();
        providerBox.getSelectionModel().select(provider);
        endpointField.setText(builtInLocal ? "https://api.example.com/v1" : settings.getEndpoint());
        refreshModelChoices("", provider);
        String model = builtInLocal ? "" : settings.getModel();
        modelBox.setValue(model);
        modelBox.getEditor().setText(model);
        apiKeyStatusLabel.setText(settings.maskedApiKey());
        applySavedSelections(settings);
        applyBundledLocalUi();
        registrationStatusLabel.setText(statusText(settings));
        downloadStatusLabel.setText("");
    }

    private AiSettings formSettings(boolean preserveExistingKey) {
        if (builtInLocalAiRadio.isSelected()) {
            return defaultLocalSettings();
        }
        AiSettings current = database.getAiSettings();
        String enteredKey = textValue(apiKeyField);
        String apiKey = preserveExistingKey && enteredKey.isBlank() ? current.getApiKey() : enteredKey;
        String provider = valueOrDefault(providerBox.getValue(), AiSettings.PROVIDER_OPENAI_COMPATIBLE);
        String endpoint = textValue(endpointField);
        String model = comboText(modelBox);
        return new AiSettings(
                true,
                provider,
                provider,
                endpoint,
                model,
                apiKey,
                selectedAgentNames(),
                selectedExtensionNames(),
                statusForSavedKey(
                        provider,
                        endpoint,
                        apiKey
                ),
                false
        );
    }

    private boolean isSavedAiConfiguration(AiSettings settings) {
        AiSettings saved = database.getAiSettings();
        return saved.getProvider().equals(settings.getProvider())
                && saved.getEndpoint().equals(settings.getEndpoint())
                && saved.getModel().equals(settings.getModel())
                && saved.getApiKey().equals(settings.getApiKey());
    }

    private void applyProviderDefaults(String oldValue, String newValue) {
        if (newValue == null) {
            return;
        }
        String currentEndpoint = textValue(endpointField);
        String defaultEndpoint = defaultEndpointFor(newValue);
        boolean providerDefaultEndpoint = currentEndpoint.isBlank()
                || currentEndpoint.equals(defaultEndpointFor(oldValue))
                || currentEndpoint.equals(AiSettings.DEFAULT_ENDPOINT)
                || currentEndpoint.equals("http://localhost:11434");
        if (providerDefaultEndpoint && !defaultEndpoint.isBlank()) {
            endpointField.setText(defaultEndpoint);
        }
    }

    private void refreshModelChoices(String oldProvider, String newProvider) {
        String currentModel = comboText(modelBox);
        boolean shouldUseProviderDefault = currentModel.isBlank()
                || modelChoicesFor(oldProvider).contains(currentModel);
        List<String> choices = modelChoicesFor(newProvider);
        modelBox.setItems(FXCollections.observableArrayList(choices));
        if (shouldUseProviderDefault && !choices.isEmpty()) {
            modelBox.setValue(choices.getFirst());
            modelBox.getEditor().setText(choices.getFirst());
            return;
        }
        if (!currentModel.isBlank()) {
            modelBox.setValue(currentModel);
            modelBox.getEditor().setText(currentModel);
        }
    }

    private List<String> modelChoicesFor(String provider) {
        return switch (valueOrDefault(provider, AiSettings.PROVIDER_OPENAI_COMPATIBLE)) {
            case AiSettings.PROVIDER_OPENAI -> List.of(
                    "gpt-5.6",
                    "gpt-5.6-sol",
                    "gpt-5.6-terra",
                    "gpt-5.6-luna",
                    "gpt-5.4",
                    "gpt-5",
                    "gpt-5-mini",
                    "gpt-5-nano",
                    "gpt-4.1",
                    "gpt-4.1-mini",
                    "gpt-4.1-nano",
                    "gpt-4o",
                    "gpt-4o-mini",
                    "gpt-realtime-2.1",
                    "gpt-realtime-2.1-mini",
                    "gpt-realtime-2",
                    "gpt-realtime-translate",
                    "gpt-realtime-1.5",
                    "gpt-realtime-whisper",
                    "gpt-4o-transcribe",
                    "gpt-4o-mini-transcribe",
                    "gpt-image-2",
                    "text-embedding-3-large",
                    "text-embedding-3-small",
                    "omni-moderation-latest"
            );
            case AiSettings.PROVIDER_OPENAI_COMPATIBLE -> List.of(
                    "gpt-5.6",
                    "gpt-5-mini",
                    "gpt-4o-mini",
                    "llama-3.3-70b-versatile",
                    "llama-3.1-8b-instant",
                    "openai/gpt-oss-120b",
                    "openai/gpt-oss-20b",
                    "qwen/qwen3.6-27b",
                    "deepseek-chat",
                    "deepseek-reasoner",
                    "deepseek-v4-flash",
                    "deepseek-v4-pro",
                    "mistral-large-latest",
                    "mistral-medium-latest",
                    "mistral-small-latest",
                    "codestral-latest",
                    "command-a-plus-05-2026",
                    "command-a-03-2025"
            );
            case AiSettings.PROVIDER_OPENROUTER -> List.of(
                    "openai/gpt-5.6",
                    "openai/gpt-5-mini",
                    "openai/gpt-4o-mini",
                    "anthropic/claude-fable-5",
                    "anthropic/claude-opus-4.8",
                    "anthropic/claude-sonnet-5",
                    "anthropic/claude-haiku-4.5",
                    "google/gemini-3.6-flash",
                    "google/gemini-3.5-flash",
                    "google/gemini-3.5-flash-lite",
                    "google/gemini-3.1-pro",
                    "google/gemini-2.5-pro",
                    "google/gemini-2.5-flash",
                    "deepseek/deepseek-v4-flash",
                    "deepseek/deepseek-v4-pro",
                    "mistralai/mistral-medium-3.5",
                    "mistralai/mistral-small-4",
                    "mistralai/mistral-large-3",
                    "meta-llama/llama-3.3-70b-instruct",
                    "qwen/qwen3.6-27b"
            );
            case AiSettings.PROVIDER_GROQ -> List.of(
                    "llama-3.1-8b-instant",
                    "llama-3.3-70b-versatile",
                    "openai/gpt-oss-120b",
                    "openai/gpt-oss-20b",
                    "groq/compound",
                    "groq/compound-mini",
                    "whisper-large-v3",
                    "whisper-large-v3-turbo",
                    "canopylabs/orpheus-arabic-saudi",
                    "canopylabs/orpheus-v1-english",
                    "meta-llama/llama-prompt-guard-2-22m",
                    "meta-llama/llama-prompt-guard-2-86m",
                    "minimaxai/minimax-m2.7",
                    "openai/gpt-oss-safeguard-20b",
                    "qwen/qwen3.6-27b"
            );
            case AiSettings.PROVIDER_DEEPSEEK -> List.of(
                    "deepseek-v4-flash",
                    "deepseek-v4-pro",
                    "deepseek-chat",
                    "deepseek-reasoner"
            );
            case AiSettings.PROVIDER_MISTRAL -> List.of(
                    "mistral-medium-latest",
                    "mistral-medium-2604",
                    "mistral-small-latest",
                    "mistral-small-2603",
                    "mistral-large-latest",
                    "mistral-large-2512",
                    "ministral-14b-2512",
                    "ministral-8b-2512",
                    "ministral-3b-2512",
                    "codestral-latest",
                    "codestral-2508",
                    "devstral-2512",
                    "labs-leanstral-1.5",
                    "mistral-ocr-latest",
                    "mistral-ocr-4",
                    "mistral-ocr-3",
                    "mistral-embed",
                    "mistral-moderation-latest",
                    "voxtral-small-latest",
                    "voxtral-mini-latest",
                    "voxtral-mini-transcribe-2602",
                    "voxtral-mini-transcribe-realtime-2602",
                    "voxtral-tts-2603",
                    "mistral-medium-2508",
                    "mistral-small-2506",
                    "magistral-medium-2509",
                    "magistral-small-2509",
                    "open-mistral-nemo-2407",
                    "open-mistral-7b",
                    "open-mixtral-8x22b",
                    "open-mixtral-8x7b"
            );
            case AiSettings.PROVIDER_ANTHROPIC -> List.of(
                    "claude-fable-5",
                    "claude-mythos-5",
                    "claude-mythos-preview",
                    "claude-opus-4-8",
                    "claude-sonnet-5",
                    "claude-haiku-4-5",
                    "claude-haiku-4-5-20251001",
                    "claude-opus-4-7",
                    "claude-opus-4-6",
                    "claude-sonnet-4-6",
                    "claude-sonnet-4-5",
                    "claude-opus-4",
                    "claude-sonnet-4",
                    "claude-3-7-sonnet-latest",
                    "claude-3-5-sonnet-latest",
                    "claude-3-5-haiku-latest"
            );
            case AiSettings.PROVIDER_GEMINI -> List.of(
                    "gemini-3.6-flash",
                    "gemini-3.5-flash",
                    "gemini-3.5-flash-lite",
                    "gemini-3.1-pro",
                    "gemini-3-flash",
                    "gemini-3.1-flash-live",
                    "gemini-3.1-flash-tts",
                    "gemini-flash-latest",
                    "gemini-pro-latest",
                    "gemini-2.5-pro",
                    "gemini-2.5-flash",
                    "gemini-2.5-flash-lite",
                    "gemini-2.5-flash-live-preview",
                    "gemini-2.5-flash-tts-preview",
                    "gemini-2.5-pro-tts-preview",
                    "gemini-embedding-2",
                    "gemini-embedding",
                    "gemini-deep-research-preview",
                    "gemini-deep-research-max-preview",
                    "computer-use-preview",
                    "imagen-4",
                    "nano-banana-2",
                    "nano-banana-2-lite",
                    "nano-banana-pro",
                    "veo-3.1-preview",
                    "veo-3.1-lite-preview",
                    "lyria-3-pro-preview",
                    "lyria-3-clip-preview",
                    "lyria-realtime-exp",
                    "gemini-2.0-flash",
                    "gemini-2.0-flash-lite"
            );
            case AiSettings.PROVIDER_COHERE -> List.of(
                    "command-a-plus-05-2026",
                    "command-a-03-2025",
                    "command-r7b-12-2024",
                    "command-a-translate-08-2025",
                    "command-a-reasoning-08-2025",
                    "command-a-vision-07-2025",
                    "command-r-plus-08-2024",
                    "command-r-08-2024",
                    "command-r-plus-04-2024",
                    "command-r-03-2024",
                    "command-r-plus",
                    "command-r",
                    "command",
                    "command-light",
                    "embed-v4.0",
                    "embed-english-v3.0",
                    "embed-english-light-v3.0",
                    "embed-multilingual-v3.0",
                    "embed-multilingual-light-v3.0",
                    "rerank-v4.0-pro",
                    "rerank-v3.5",
                    "aya-vision-32b",
                    "aya-expanse-32b",
                    "aya-expanse-8b",
                    "north-mini-code"
            );
            case AiSettings.PROVIDER_LOCAL_LLAMA, AiSettings.PROVIDER_BUNDLED_LOCAL -> List.of(AiSettings.BUNDLED_LOCAL_MODEL);
            case AiSettings.PROVIDER_OLLAMA -> List.of(
                    "llama3.3",
                    "llama3.2",
                    "llama3.1",
                    "llama2",
                    "qwen3",
                    "qwen2.5",
                    "qwen2.5-coder",
                    "mistral",
                    "mixtral",
                    "gemma3",
                    "gemma2",
                    "deepseek-r1",
                    "codellama",
                    "phi4",
                    "phi3",
                    "nomic-embed-text"
            );
            default -> List.of("model-name");
        };
    }

    private String defaultEndpointFor(String provider) {
        return switch (valueOrDefault(provider, AiSettings.PROVIDER_OPENAI_COMPATIBLE)) {
            case AiSettings.PROVIDER_OPENAI -> "https://api.openai.com/v1";
            case AiSettings.PROVIDER_OPENAI_COMPATIBLE -> "https://api.example.com/v1";
            case AiSettings.PROVIDER_OPENROUTER -> "https://openrouter.ai/api/v1";
            case AiSettings.PROVIDER_GROQ -> "https://api.groq.com/openai/v1";
            case AiSettings.PROVIDER_DEEPSEEK -> "https://api.deepseek.com/v1";
            case AiSettings.PROVIDER_MISTRAL -> "https://api.mistral.ai/v1";
            case AiSettings.PROVIDER_ANTHROPIC -> "https://api.anthropic.com/v1";
            case AiSettings.PROVIDER_GEMINI -> "https://generativelanguage.googleapis.com/v1beta";
            case AiSettings.PROVIDER_COHERE -> "https://api.cohere.com/v2";
            case AiSettings.PROVIDER_LOCAL_LLAMA, AiSettings.PROVIDER_BUNDLED_LOCAL -> AiSettings.BUNDLED_LOCAL_ENDPOINT;
            case AiSettings.PROVIDER_OLLAMA -> "http://localhost:11434";
            default -> "";
        };
    }

    private void applyBundledLocalUi() {
        boolean builtInLocal = builtInLocalAiRadio.isSelected();
        builtInLocalAiPane.setManaged(builtInLocal);
        builtInLocalAiPane.setVisible(builtInLocal);
        customAiProviderPane.setManaged(!builtInLocal);
        customAiProviderPane.setVisible(!builtInLocal);
        if (builtInLocal) {
            apiKeyField.clear();
            apiKeyStatusLabel.setText("No API key required for PFMIS Local AI");
            bundledLocalExtensionBox.setSelected(true);
            localAiExtensionBox.setSelected(true);
        }
    }

    private void refreshLocalAiStatus() {
        if (localAiStatusLabel == null) {
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            if (!Files.isRegularFile(BundledLocalAiManager.serverExecutable())
                    || !Files.isRegularFile(BundledLocalAiManager.modelFile())) {
                return "Not Available";
            }
            String status = BundledLocalAiManager.healthStatus();
            if ("ok".equalsIgnoreCase(status)) {
                return "Running";
            }
            if ("not available".equalsIgnoreCase(status)) {
                return "Not Available";
            }
            return "Starting";
        }).whenComplete((status, throwable) -> Platform.runLater(() -> {
            if (throwable == null) {
                localAiStatusLabel.setText("Status: " + status);
            } else {
                localAiStatusLabel.setText("Status: Not Available");
            }
        }));
    }

    private AiSettings defaultLocalSettings() {
        String agents = selectedAgentNames();
        String extensions = selectedExtensionNames();
        return new AiSettings(
                true,
                AiSettings.DEFAULT_DISPLAY_NAME,
                AiSettings.PROVIDER_LOCAL_LLAMA,
                AiSettings.DEFAULT_ENDPOINT,
                AiSettings.DEFAULT_MODEL,
                "",
                agents.isBlank() ? AiSettings.DEFAULT_AGENTS : agents,
                extensions.isBlank() ? AiSettings.DEFAULT_EXTENSIONS : extensions,
                AiSettings.KEY_STATUS_ACTIVE,
                true
        );
    }

    private String comboText(ComboBox<String> comboBox) {
        String editorText = comboBox.getEditor() == null ? "" : comboBox.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        return valueOrDefault(comboBox.getValue(), "");
    }

    private void applySavedSelections(AiSettings settings) {
        setSelected(pfmisCopilotAgentBox, settings.getAgents(), "PFMIS Copilot");
        setSelected(transactionCoachAgentBox, settings.getAgents(), "Transaction Coach");
        setSelected(dataQualityGuardianAgentBox, settings.getAgents(), "Data Quality Guardian");
        setSelected(goalCoachAgentBox, settings.getAgents(), "Goal Coach");
        setSelected(budgetAnalystAgentBox, settings.getAgents(), "Budget Analyst");
        setSelectedAny(loanAdvisorAgentBox, settings.getAgents(), "Loan Review", "Loan Advisor");
        setSelectedAny(projectAdvisorAgentBox, settings.getAgents(), "Project Spending Review", "Project Spending Advisor");
        setSelected(newUserAgentBox, settings.getAgents(), "New User Guide");
        setSelected(backupGuardianAgentBox, settings.getAgents(), "Backup Guardian");
        setSelected(openAiConnectorExtensionBox, settings.getExtensions(), "OpenAI Connector");
        setSelected(openRouterConnectorExtensionBox, settings.getExtensions(), "OpenRouter Connector");
        setSelected(geminiConnectorExtensionBox, settings.getExtensions(), "Gemini Connector");
        setSelected(claudeConnectorExtensionBox, settings.getExtensions(), "Claude Connector");
        setSelected(cohereConnectorExtensionBox, settings.getExtensions(), "Cohere Connector");
        setSelectedAny(bundledLocalExtensionBox, settings.getExtensions(), "Bundled Local Runtime", "Bundled Local AI Runtime");
        setSelectedAny(localAiExtensionBox, settings.getExtensions(), "Local Provider Connector", "Local AI Connector");
        setSelected(csvInsightExtensionBox, settings.getExtensions(), "CSV Insight Pack");
        setSelected(receiptTemplateExtensionBox, settings.getExtensions(), "Receipt Import Template");
        setSelected(backupGuideExtensionBox, settings.getExtensions(), "Backup Guide");
    }

    private void setSelected(CheckBox checkBox, String csv, String name) {
        checkBox.setSelected(csv != null && List.of(csv.split(",")).stream()
                .map(String::trim)
                .anyMatch(name::equals));
    }

    private void setSelectedAny(CheckBox checkBox, String csv, String... names) {
        checkBox.setSelected(csv != null && List.of(csv.split(",")).stream()
                .map(String::trim)
                .anyMatch(savedName -> List.of(names).contains(savedName)));
    }

    private String selectedAgentNames() {
        List<String> names = new ArrayList<>();
        addIfSelected(names, pfmisCopilotAgentBox, "PFMIS Copilot");
        addIfSelected(names, transactionCoachAgentBox, "Transaction Coach");
        addIfSelected(names, dataQualityGuardianAgentBox, "Data Quality Guardian");
        addIfSelected(names, goalCoachAgentBox, "Goal Coach");
        addIfSelected(names, budgetAnalystAgentBox, "Budget Analyst");
        addIfSelected(names, loanAdvisorAgentBox, "Loan Review");
        addIfSelected(names, projectAdvisorAgentBox, "Project Spending Review");
        addIfSelected(names, newUserAgentBox, "New User Guide");
        addIfSelected(names, backupGuardianAgentBox, "Backup Guardian");
        return String.join(",", names);
    }

    private String selectedExtensionNames() {
        List<String> names = new ArrayList<>();
        addIfSelected(names, openAiConnectorExtensionBox, "OpenAI Connector");
        addIfSelected(names, openRouterConnectorExtensionBox, "OpenRouter Connector");
        addIfSelected(names, geminiConnectorExtensionBox, "Gemini Connector");
        addIfSelected(names, claudeConnectorExtensionBox, "Claude Connector");
        addIfSelected(names, cohereConnectorExtensionBox, "Cohere Connector");
        addIfSelected(names, bundledLocalExtensionBox, "Bundled Local Runtime");
        addIfSelected(names, localAiExtensionBox, "Local Provider Connector");
        addIfSelected(names, csvInsightExtensionBox, "CSV Insight Pack");
        addIfSelected(names, receiptTemplateExtensionBox, "Receipt Import Template");
        addIfSelected(names, backupGuideExtensionBox, "Backup Guide");
        return String.join(",", names);
    }

    private void addIfSelected(List<String> names, CheckBox checkBox, String name) {
        if (checkBox.isSelected()) {
            names.add(name);
        }
    }

    private List<StarterItem> selectedStarterItems() {
        List<StarterItem> items = new ArrayList<>();
        addAgentItem(items, pfmisCopilotAgentBox, "pfmis-copilot.agent.json", "PFMIS Copilot", "Provides context-aware, read-only guidance on every PFMIS page.");
        addAgentItem(items, transactionCoachAgentBox, "transaction-coach.agent.json", "Transaction Coach", "Reviews transaction classification, affordability, duplicate risk, and budget impact before saving.");
        addAgentItem(items, dataQualityGuardianAgentBox, "data-quality-guardian.agent.json", "Data Quality Guardian", "Detects incomplete, inconsistent, stale, duplicated, and mixed-currency records.");
        addAgentItem(items, goalCoachAgentBox, "goal-coach.agent.json", "Goal Coach", "Turns registered goals into monthly saving actions.");
        addAgentItem(items, budgetAnalystAgentBox, "budget-analyst.agent.json", "Budget Analyst", "Finds spending pressure and budget adjustment options.");
        addAgentItem(items, loanAdvisorAgentBox, "loan-review.agent.json", "Loan Review", "Reviews borrowed, lent, and repayment records before giving goal advice.");
        addAgentItem(items, projectAdvisorAgentBox, "project-spending-review.agent.json", "Project Spending Review", "Checks project spending against planned budgets.");
        addAgentItem(items, newUserAgentBox, "new-user-guide.agent.json", "New User Guide", "Guides first-time users through accounts, categories, goals, and Smart Analysis registration.");
        addAgentItem(items, backupGuardianAgentBox, "backup-guardian.agent.json", "Backup Guardian", "Checks backup recency and recommends verified backups before high-impact changes.");
        addExtensionItem(items, openAiConnectorExtensionBox, "openai-connector.extension.json", "OpenAI Connector", "Uses an OpenAI-compatible HTTPS chat endpoint.");
        addExtensionItem(items, openRouterConnectorExtensionBox, "openrouter-connector.extension.json", "OpenRouter Connector", "Uses OpenRouter with provider-prefixed model IDs.");
        addExtensionItem(items, geminiConnectorExtensionBox, "gemini-connector.extension.json", "Gemini Connector", "Uses the Google Gemini generateContent endpoint.");
        addExtensionItem(items, claudeConnectorExtensionBox, "claude-connector.extension.json", "Claude Connector", "Uses the Anthropic Messages endpoint.");
        addExtensionItem(items, cohereConnectorExtensionBox, "cohere-connector.extension.json", "Cohere Connector", "Uses the Cohere chat endpoint.");
        addExtensionItem(items, bundledLocalExtensionBox, "bundled-local-runtime.extension.json", "Bundled Local Runtime", "Packages llama-server.exe and pfmis-model.gguf for offline recommendations.");
        addExtensionItem(items, localAiExtensionBox, "local-provider-connector.extension.json", "Local Provider Connector", "Uses a local Ollama-compatible endpoint.");
        addExtensionItem(items, csvInsightExtensionBox, "csv-insight-pack.extension.json", "CSV Insight Pack", "Exports summaries that can be attached to external review tools.");
        addExtensionItem(items, receiptTemplateExtensionBox, "receipt-import-template.extension.json", "Receipt Import Template", "Defines receipt fields for future import workflows.");
        addExtensionItem(items, backupGuideExtensionBox, "backup-guide.extension.json", "Backup Guide", "Documents how to protect pfmis.db before provider experiments.");
        return items;
    }

    private void addAgentItem(List<StarterItem> items, CheckBox checkBox, String fileName, String name, String purpose) {
        if (checkBox.isSelected()) {
            items.add(new StarterItem(fileName, descriptor("agent", name, purpose)));
        }
    }

    private void addExtensionItem(List<StarterItem> items, CheckBox checkBox, String fileName, String name, String purpose) {
        if (checkBox.isSelected()) {
            items.add(new StarterItem(fileName, descriptor("extension", name, purpose)));
        }
    }

    private String descriptor(String type, String name, String purpose) {
        return "{\n"
                + "  \"type\": \"" + type + "\",\n"
                + "  \"name\": \"" + name + "\",\n"
                + "  \"purpose\": \"" + purpose + "\",\n"
                + "  \"app\": \"PFMIS\"\n"
                + "}\n";
    }

    private String starterReadme(List<StarterItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("PFMIS Smart Analysis starter pack\n\n");
        builder.append("Register the provider in Settings, enable the profiles you want, then open Smart Analysis under Administration.\n\n");
        builder.append("Downloaded items:\n");
        for (StarterItem item : items) {
            builder.append("- ").append(item.fileName()).append('\n');
        }
        return builder.toString();
    }

    private String statusText(AiSettings settings) {
        if (settings.isBundledLocalProvider()) {
            return settings.isEnabled()
                    ? "PFMIS Local AI active. PFMIS starts the packaged local runtime automatically. No API key is required."
                    : "PFMIS Local AI is configured but disabled.";
        }
        if (AiSettings.KEY_STATUS_INACTIVE.equals(settings.getKeyStatus())) {
            return "API key INACTIVE. Save a valid key to reactivate recommendations.";
        }
        if (AiSettings.KEY_STATUS_QUOTA_LIMITED.equals(settings.getKeyStatus())) {
            return "Provider key active, but quota, billing, or rate limit is blocking requests.";
        }
        if (!settings.isEnabled()) {
            return settings.getApiKey().isBlank() && !settings.isLocalProvider()
                    ? "Smart Analysis is disabled and no API key is saved."
                    : "Provider key active, but recommendations are disabled.";
        }
        if (settings.canGenerateRecommendations()) {
            return "Smart Analysis active: " + settings.getProviderDisplayName() + " / " + settings.getModel();
        }
        return "Incomplete: enable recommendations, enter endpoint, model, and API key unless using a local provider.";
    }

    private String statusForSavedKey(String provider, String endpoint, String apiKey) {
        boolean localProvider = AiSettings.PROVIDER_LOCAL_LLAMA.equals(provider)
                || AiSettings.PROVIDER_BUNDLED_LOCAL.equals(provider)
                || provider.toLowerCase().contains("ollama")
                || endpoint.toLowerCase().contains("localhost")
                || endpoint.toLowerCase().contains("127.0.0.1");
        if (localProvider || !apiKey.isBlank()) {
            return AiSettings.KEY_STATUS_ACTIVE;
        }
        return AiSettings.KEY_STATUS_INACTIVE;
    }

    private boolean isInactiveKeyError(String errorMessage) {
        String message = errorMessage == null ? "" : errorMessage.toLowerCase();
        return message.contains("http 401")
                || message.contains("http 403")
                || message.contains("unauthorized")
                || message.contains("invalid api key")
                || message.contains("incorrect api key")
                || message.contains("permission denied")
                || message.contains("key is not active");
    }

    private boolean isQuotaOrRateLimitError(String errorMessage) {
        String message = errorMessage == null ? "" : errorMessage.toLowerCase();
        return message.contains("http 429")
                || message.contains("quota")
                || message.contains("billing")
                || message.contains("rate limit")
                || message.contains("too many requests");
    }

    private String textValue(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    private record StarterItem(String fileName, String content) {
    }
}
