package com.example.axiomai.client;

import com.example.axiomai.core.AiConfig;
import com.example.axiomai.core.BrushGenerator;
import com.example.axiomai.core.Chat;
import com.example.axiomai.core.ChatMessage;
import com.example.axiomai.core.ChatStore;
import com.example.axiomai.core.ModelProfile;
import com.example.axiomai.core.NbtPresetWriter;
import com.example.axiomai.core.UiState;
import imgui.moulberry92.ImGui;
import imgui.moulberry92.ImVec2;
import imgui.moulberry92.flag.ImGuiChildFlags;
import imgui.moulberry92.flag.ImGuiCol;
import imgui.moulberry92.flag.ImGuiCond;
import imgui.moulberry92.flag.ImGuiStyleVar;
import imgui.moulberry92.flag.ImGuiWindowFlags;
import imgui.moulberry92.type.ImBoolean;
import imgui.moulberry92.type.ImString;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;

/**
 * The AI chat window, drawn with Axiom's ImGui. It is a normal ImGui window, so it can be moved, resized and docked.
 * It is drawn on every frame of Axiom's editor (through a small hook), so it stays on screen when another tool is
 * selected. Chats are kept on disk and removed after a period without activity.
 */
final class ChatUi {
    private static final Logger LOGGER = LoggerFactory.getLogger("AxiomAIAddon");
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AxiomAI-worker");
        t.setDaemon(true);
        return t;
    });
    private static final long PURGE_INTERVAL_MS = 10 * 60 * 1000L;
    private static final long HOOK_TIMEOUT_MS = 1500L;
    private static ChatUi instance;

    static synchronized ChatUi get() {
        if (instance == null) instance = new ChatUi();
        return instance;
    }

    final Path configDir;
    private final Path presetDir;
    private final Path uiFile;
    private final ChatStore store;
    final List<Chat> chats = new ArrayList<>();
    private final LuaScriptChecker checker = new LuaScriptChecker();

    Chat selected;                         // null = a new, still empty chat
    private final ImBoolean open;
    final ImString input = new ImString(4096);
    final float[] fontScale = new float[1];
    private boolean loaded;
    private boolean scrollToBottom;
    boolean focusInput;
    private int lastItemCount = -1;
    private long lastPurge;
    int retentionDays = 3;

    AiConfig cachedConfig;                 // re-read every two seconds, so edits of config.json show up quickly
    private long configLoadedAt;
    String selectedModel = "";
    private String savedModel = "";
    volatile double sessionCost;

    private boolean savedOpen;
    private float savedScale;
    private long lastUiSave;
    private volatile long lastHookFrame;

    volatile boolean busy;
    volatile String error;
    volatile String stage = "";

    // state of the modern look
    boolean modern = true;
    boolean sidebarOpen = true;
    final ImString search = new ImString(128);
    String renamingId;
    Chat pendingDelete;
    int inputGen;
    boolean inputWasActive;
    boolean scrollRequest;
    final java.util.Set<Long> fresh = ConcurrentHashMap.newKeySet();   // answers that are "typed" onto the screen
    private final ChatView view = new ChatView(this);
    private volatile long generation;
    private Future<?> task;
    private boolean wasOpen;
    private int lastTrackedFrame = -1;
    private boolean savedModern = true;
    private boolean savedSidebar = true;

    /** When a chat refines its brush, the same preset is changed instead of saving a new one. */
    boolean updatePreset = true;
    private boolean savedUpdatePreset = true;
    /** The four idea cards of the start screen (new ones are drawn for every new chat). */
    List<Ideas.Idea> ideas = Ideas.random(4);
    /** Set by any thread after a preset file was written; the render thread then makes Axiom reload its list. */
    private volatile boolean presetRefreshNeeded;

    private ChatUi() {
        this(FabricLoader.getInstance().getConfigDir());
    }

    /** Also used by the screenshot tests, with a temporary folder instead of the real game folder. */
    ChatUi(Path gameConfig) {
        this.configDir = gameConfig.resolve("axiom_ai_addon");
        this.presetDir = NbtPresetWriter.axiomFolder(gameConfig);
        this.uiFile = configDir.resolve("ui.json");
        this.store = new ChatStore(configDir.resolve("chats"));
        UiState ui = UiState.load(uiFile);
        this.open = new ImBoolean(ui.open);
        this.fontScale[0] = ui.fontScale;
        this.savedOpen = ui.open;
        this.savedScale = ui.fontScale;
        this.selectedModel = ui.model;
        this.savedModel = ui.model;
        this.modern = ui.modern;
        this.savedModern = ui.modern;
        this.sidebarOpen = ui.sidebar;
        this.savedSidebar = ui.sidebar;
        this.updatePreset = ui.updatePreset;
        this.savedUpdatePreset = ui.updatePreset;
    }

    // ------------------------------------------------------------------ test helpers

    List<Chat> chatsForTest() {
        ensureLoaded();
        return chats;
    }

    void selectForTest(Chat chat) {
        selected = chat;
    }

    void stateForTest(boolean isBusy, String errorText) {
        busy = isBusy;
        error = errorText;
    }

    float fadeForTest() {
        return Anim.smooth("ai.windowfade", 1f, 0f);   // speed 0: only reads the current value
    }

    Path presetDirForTest() {
        return presetDir;
    }

    boolean refreshPendingForTest() {
        return presetRefreshNeeded;
    }

    ChatView viewForTest() {
        return view;
    }

    void stageForTest(String code) {
        stage = code;
    }

    // ------------------------------------------------------------------ entry points

    /** Called on every frame of Axiom's editor by the hook: the window is visible whatever tool is selected. */
    void drawFromHook() {
        lastHookFrame = System.currentTimeMillis();
        applyPresetRefresh();
        ensureLoaded();
        maybePurge();
        if (open.get()) drawWindow();
        else trackOpen();
        persistUi();
    }

    /** Tool Options panel of the "AI Create" tool. Draws the window itself only if the per-frame hook is missing. */
    void drawToolPanel() {
        applyPresetRefresh();
        ensureLoaded();
        maybePurge();
        ImGui.textWrapped("Describe a brush in the AI chat. It is saved as a Script Brush preset.");
        if (ImGui.button(open.get() ? "Close AI chat window" : "Open AI chat window")) {
            open.set(!open.get());
        }
        if (open.get() && !hookActive()) drawWindow();
        persistUi();
    }

    /** Makes Axiom read the preset folder again (render thread only), so that new presets appear at once. */
    private void applyPresetRefresh() {
        if (!presetRefreshNeeded) return;
        presetRefreshNeeded = false;
        PresetRefresher.refreshScriptBrush();
    }

    private boolean hookActive() {
        return System.currentTimeMillis() - lastHookFrame < HOOK_TIMEOUT_MS;
    }

    // ------------------------------------------------------------------ the chat window

    private void drawWindow() {
        refreshConfig();
        trackOpen();
        // Decide the look once: the checkbox inside the window can change 'modern' while the window is
        // being drawn, and every push of a style below must get its matching pop.
        final boolean useModern = modern;
        ImGui.setNextWindowSize(useModern ? 1000f : 800f, useModern ? 640f : 560f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSizeConstraints(useModern ? 560f : 480f, 340f, 4000f, 3000f);
        if (useModern) {
            float fade = Anim.smooth("ai.windowfade", 1f, 14f);
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 0.2f + 0.8f * fade);
            ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 0f, 0f);
            ImGui.pushStyleColor(ImGuiCol.WindowBg, Kit.BG_MAIN[0], Kit.BG_MAIN[1], Kit.BG_MAIN[2], 1f);
        }
        boolean visible = ImGui.begin("AI Create##aicreate_window", open);
        if (visible) {
            if (useModern) {
                applyScale();
                view.draw();
            } else {
                drawContents();
            }
        }
        ImGui.end();
        if (useModern) {
            ImGui.popStyleColor();
            ImGui.popStyleVar(2);
        }
    }

    /**
     * Starts the fade-in animation when the window was closed and is open now. Both entry points (the hook and
     * the tool panel) can run in one frame, so this is evaluated only once per frame.
     */
    private void trackOpen() {
        int frame = ImGui.getFrameCount();
        if (frame == lastTrackedFrame) return;
        lastTrackedFrame = frame;
        boolean isOpen = open.get();
        if (isOpen && !wasOpen) Anim.reset("ai.windowfade", 0f);
        wasOpen = isOpen;
    }

    private void drawContents() {
        applyScale();
        ImVec2 avail = ImGui.getContentRegionAvail();
        float leftWidth = Math.max(160f, avail.x * 0.27f);

        if (ImGui.beginChild("##aichats", leftWidth, avail.y, ImGuiChildFlags.Borders)) {
            applyScale();
            drawChatList();
        }
        ImGui.endChild();

        ImGui.sameLine();

        if (ImGui.beginChild("##aipane", 0f, avail.y)) {
            applyScale();
            drawChatPane();
        }
        ImGui.endChild();
    }

    private void drawChatList() {
        float width = ImGui.getContentRegionAvail().x;
        if (ImGui.button("+ New chat", width, 0f)) {
            selected = null;
            error = null;
            scrollToBottom = true;
            focusInput = true;
        }
        ImGui.separator();
        for (Chat chat : chats) {
            String label = chat.title() + "  (" + age(chat.lastActive) + ")##" + chat.id;
            if (ImGui.selectable(label, chat == selected)) selectChat(chat);
        }
        ImGui.separator();
        if (retentionDays > 0) {
            ImGui.pushTextWrapPos();
            ImGui.textDisabled("Chats with no activity for " + retentionDays + " days are deleted automatically.");
            ImGui.popTextWrapPos();
        }
    }

    private void drawChatPane() {
        ImGui.text(selected == null ? "New chat" : selected.title());

        ImGui.setNextItemWidth(120f * fontScale[0]);
        ImGui.sliderFloat("Text size##aifs", fontScale, 0.8f, 1.8f, "%.1f");
        ImGui.sameLine();
        ImGui.beginDisabled(selected == null);
        if (ImGui.button("Delete chat")) ImGui.openPopup("Delete this chat?##aidel");
        ImGui.endDisabled();
        ImGui.sameLine();
        if (ImGui.checkbox("Modern look##aimodernclassic", modern)) modern = !modern;   // the way back to the new look

        if (ImGui.beginPopupModal("Delete this chat?##aidel")) {
            ImGui.text("The chat history will be removed.");
            ImGui.text("Brushes already saved in Axiom stay where they are.");
            ImGui.spacing();
            if (ImGui.button("Delete", 110f, 0f)) {
                deleteSelected();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("Cancel", 110f, 0f)) ImGui.closeCurrentPopup();
            ImGui.endPopup();
        }

        AiConfig cfg = cachedConfig;
        if (cfg != null && cfg.hasModelChoice()) {
            ImGui.setNextItemWidth(260f * fontScale[0]);
            if (ImGui.beginCombo("Model##aimodel", selectedModel)) {
                for (ModelProfile m : cfg.models) {
                    String label = m.name() + (m.priceText().isEmpty() ? "" : "   (" + m.priceText() + ")");
                    if (ImGui.selectable(label + "##model_" + m.name(), m.name().equals(selectedModel))) {
                        selectedModel = m.name();
                    }
                }
                ImGui.endCombo();
            }
            if (sessionCost > 0) {
                ImGui.sameLine();
                ImGui.textDisabled(String.format(Locale.ROOT, "spent this session: about %.2f RUB", sessionCost));
            }
        }

        ImGui.separator();

        float inputHeight = ImGui.getTextLineHeight() * 3f + 16f;
        float messagesHeight = Math.max(80f, ImGui.getContentRegionAvail().y - inputHeight - 14f);
        drawMessages(messagesHeight);
        drawInput(inputHeight);
    }

    private void drawMessages(float height) {
        if (ImGui.beginChild("##aimsgs", 0f, height, ImGuiChildFlags.Borders)) {
            applyScale();
            int itemCount = 0;
            if (selected == null || selected.messages.isEmpty()) {
                wrapped("Describe the brush you want, for example: \"scatter flowers on grass with a density slider\". "
                        + "Then keep writing to adjust it, for example: \"make it denser\".");
            } else {
                int index = 0;
                for (ChatMessage message : selected.messages) {
                    ImGui.pushID(index++);
                    drawMessage(message);
                    ImGui.popID();
                    ImGui.spacing();
                    itemCount++;
                }
            }
            if (!busy && selected != null && !selected.messages.isEmpty()
                    && !selected.messages.get(selected.messages.size() - 1).isUser()) {
                boolean choice = cachedConfig != null && cachedConfig.hasModelChoice();
                if (ImGui.button("Regenerate" + (choice ? " with " + selectedModel : "") + "##airegen")) regenerate();
                ImGui.sameLine();
                ImGui.textDisabled("same request, new answer (costs again)");
                itemCount++;
            }
            if (busy) {
                int dots = (int) ((System.currentTimeMillis() / 400) % 4);
                ImGui.textColored(0.95f, 0.85f, 0.40f, 1f, "AI is writing and testing the brush" + ".".repeat(dots));
                itemCount++;
            }
            String err = error;
            if (err != null) {
                ImGui.textColored(1.00f, 0.45f, 0.45f, 1f, "Error");
                wrapped(err);
                if (!busy && selected != null && !selected.messages.isEmpty()
                        && selected.messages.get(selected.messages.size() - 1).isUser()) {
                    if (ImGui.button("Retry")) retry();
                    ImGui.sameLine();
                }
                if (ImGui.button("Copy config folder path")) ImGui.setClipboardText(configDir.toString());
                itemCount++;
            }
            if (scrollToBottom || itemCount != lastItemCount) {
                ImGui.setScrollHereY(1.0f);
                lastItemCount = itemCount;
                scrollToBottom = false;
            }
        }
        ImGui.endChild();
    }

    private void drawMessage(ChatMessage message) {
        if (message.isUser()) {
            ImGui.textColored(0.55f, 0.80f, 1.00f, 1f, "You");
            wrapped(message.text());
            return;
        }
        ImGui.textColored(0.45f, 0.90f, 0.55f, 1f, "AI");
        if (!message.preset().isEmpty()) {
            ImGui.sameLine();
            ImGui.textDisabled("saved as preset: " + message.preset());
        }

        String mode = planValue(message.text(), "MODE");
        String action = planValue(message.text(), "ACTION");
        if (!mode.isEmpty() || !action.isEmpty()) {
            ImGui.pushTextWrapPos();
            ImGui.textColored(0.70f, 0.70f, 0.75f, 1f, "Understood as [" + mode + "]: " + action);
            ImGui.popTextWrapPos();
        }

        String code = withoutNameLine(message.text());
        float codeHeight = Math.min(countLines(code) * ImGui.getTextLineHeight() + 24f, 320f * fontScale[0]);
        if (ImGui.beginChild("##aicode", 0f, codeHeight, ImGuiChildFlags.Borders, ImGuiWindowFlags.HorizontalScrollbar)) {
            applyScale();
            ImGui.textUnformatted(code);
        }
        ImGui.endChild();
        if (ImGui.button("Copy code")) ImGui.setClipboardText(code);

        if (!message.meta().isEmpty()) {
            ImGui.pushTextWrapPos();
            ImGui.textDisabled(message.meta());
            ImGui.popTextWrapPos();
        }

        String note = message.note();
        if (!note.isEmpty()) {
            boolean good = note.startsWith("Auto-check passed") || note.startsWith("Auto-check: the AI fixed");
            ImGui.pushTextWrapPos();
            if (good) ImGui.textColored(0.45f, 0.90f, 0.55f, 1f, note);
            else ImGui.textColored(1.00f, 0.75f, 0.35f, 1f, note);
            ImGui.popTextWrapPos();
        }
    }

    private void drawInput(float inputHeight) {
        float buttonWidth = 80f * fontScale[0];
        float inputWidth = Math.max(100f, ImGui.getContentRegionAvail().x - buttonWidth - 8f);
        if (focusInput) {
            ImGui.setKeyboardFocusHere();
            focusInput = false;
        }
        ImGui.inputTextMultiline("##aiinput", input, inputWidth, inputHeight);
        ImGui.sameLine();

        float half = (inputHeight - 4f) / 2f;
        ImGui.beginGroup();
        boolean canSend = !busy && !input.get().trim().isEmpty();
        ImGui.beginDisabled(!canSend);
        if (ImGui.button("Send", buttonWidth, half)) send();
        ImGui.endDisabled();
        ImGui.beginDisabled(input.get().isEmpty());
        if (ImGui.button("Clear", buttonWidth, half)) {
            input.clear();
            focusInput = true;
        }
        ImGui.endDisabled();
        ImGui.endGroup();
    }

    // ------------------------------------------------------------------ actions

    void send() {
        String text = input.get().trim();
        if (text.isEmpty() || busy) return;

        Chat chat = selected;
        if (chat == null) {
            chat = Chat.create();
            chats.add(0, chat);
            selected = chat;
        }
        chat.add(ChatMessage.user(text));
        saveQuietly(chat);
        input.clear();
        inputGen++;            // a fresh input box, so the old text cannot come back
        startGeneration(chat);
    }

    /** Sends the last request of the open chat again (after an error), without adding a new message. */
    void retry() {
        if (busy || selected == null) return;
        List<ChatMessage> messages = selected.messages;
        if (messages.isEmpty() || !messages.get(messages.size() - 1).isUser()) return;
        selected.touch();
        startGeneration(selected);
    }

    /** Asks again for the last request (for example with another model); the older answer stays in the chat. */
    void regenerate() {
        if (busy || selected == null) return;
        List<ChatMessage> messages = selected.messages;
        int lastUser = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).isUser()) {
                lastUser = i;
                break;
            }
        }
        if (lastUser < 0) return;
        selected.touch();
        startGeneration(selected, new ArrayList<>(messages.subList(0, lastUser + 1)));
    }

    /** Cancels the running request: the connection is closed and a late answer is thrown away. */
    void stop() {
        Future<?> running = task;
        if (running != null) running.cancel(true);
        generation++;
        busy = false;
        stage = "";
        error = null;
    }

    private void startGeneration(Chat chat) {
        startGeneration(chat, new ArrayList<>(chat.messages));
    }

    private void startGeneration(Chat chat, List<ChatMessage> snapshot) {
        error = null;
        busy = true;
        stage = "";
        scrollToBottom = true;
        scrollRequest = true;
        focusInput = true;

        final Chat target = chat;
        final String modelName = selectedModel;
        final boolean updateExisting = updatePreset;
        final long myGeneration = ++generation;
        task = WORKER.submit(() -> {
            try {
                AiConfig config = AiConfig.loadOrCreate(configDir);
                ModelProfile profile = null;
                if (config.hasModelChoice()) {
                    profile = config.profileNamed(modelName);
                    if (profile == null) profile = config.defaultProfile();
                }
                BrushGenerator.Engine primary = config.createEngine(configDir, profile);
                BrushGenerator.Engine escalation = config.createEscalationEngine(configDir, profile);
                BrushGenerator generator = new BrushGenerator(primary, escalation, presetDir, checker,
                        config.fixesFor(profile)).withProgress(code -> {
                            if (generation == myGeneration) stage = code;
                        });
                String updateFile = updateExisting && !target.presetFile.isEmpty() ? target.presetFile : null;
                BrushGenerator.Result result = generator.generate(snapshot, updateFile);
                if (generation != myGeneration) return;   // the user pressed Stop meanwhile
                sessionCost += result.costRub();
                target.presetFile = result.file().getFileName().toString();
                target.presetName = result.presetName();
                ChatMessage reply = ChatMessage.assistant(result.assistantText(), result.presetName(),
                        result.note(), result.meta(), result.updated());
                target.add(reply);
                fresh.add(reply.time());
                saveQuietly(target);
                presetRefreshNeeded = true;
            } catch (Throwable t) {
                if (generation == myGeneration && !(t instanceof InterruptedException)) {
                    LOGGER.warn("AI request failed", t);
                    error = describe(t);
                }
            } finally {
                if (generation == myGeneration) {
                    busy = false;
                    stage = "";
                }
                Thread.interrupted();   // a stale interrupt must not disturb the next request
            }
        });
    }

    void selectChat(Chat chat) {
        if (chat == selected) return;
        selected = chat;
        chat.touch();
        saveQuietly(chat);
        error = null;
        scrollToBottom = true;
        scrollRequest = true;
        renamingId = null;
    }

    void rollIdeas() {
        ideas = Ideas.random(4);
    }

    void newChat() {
        rollIdeas();
        selected = null;
        error = null;
        scrollToBottom = true;
        scrollRequest = true;
        focusInput = true;
        renamingId = null;
    }

    void renameChat(Chat chat, String title) {
        chat.customTitle = title == null ? "" : title.trim();
        saveQuietly(chat);
        renamingId = null;
    }

    /** Puts a suggestion into the input box (the user decides when to send it). */
    void fillInput(String text) {
        input.set(text);
        inputGen++;
        focusInput = true;
    }

    /** Deletes the chat that the user confirmed in the dialog. */
    void deletePending() {
        Chat chat = pendingDelete;
        pendingDelete = null;
        if (chat == null) return;
        store.delete(chat);
        chats.remove(chat);
        if (chat == selected) {
            selected = null;
            error = null;
            rollIdeas();
        }
        renamingId = null;
    }

    /**
     * Gives the preset of the open chat a new name (the file is renamed too). Returns null when it worked,
     * otherwise a message for the user.
     */
    String renamePreset(String newName) {
        Chat chat = selected;
        if (chat == null || busy || chat.presetFile.isEmpty()) return "";
        String name = newName == null ? "" : newName.strip();
        if (name.isEmpty()) return "";
        try {
            NbtPresetWriter.Saved saved = NbtPresetWriter.rename(presetDir, presetDir.resolve(chat.presetFile), name);
            chat.presetFile = saved.file().getFileName().toString();
            chat.presetName = saved.presetName();
            List<ChatMessage> messages = chat.messages;
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage m = messages.get(i);
                if (!m.isUser() && !m.preset().isEmpty()) {
                    messages.set(i, m.withTextAndPreset(withNewNameLine(m.text(), saved.presetName()), saved.presetName()));
                    break;
                }
            }
            chat.touch();
            saveQuietly(chat);
            presetRefreshNeeded = true;
            return null;
        } catch (java.nio.file.NoSuchFileException e) {
            error = Tr.t("The preset file is gone (it was deleted). Send the request again to create it.");
            return error;
        } catch (Exception e) {
            error = describe(e);
            return error;
        }
    }

    /** "-- NAME: old" on the first line becomes "-- NAME: new" (the NAMES ideas stay). */
    private static String withNewNameLine(String text, String presetName) {
        if (!text.startsWith("-- NAME:")) return text;
        int nl = text.indexOf('\n');
        String shown = presetName.startsWith("AI ") ? presetName.substring(3) : presetName;
        return "-- NAME: " + shown + (nl < 0 ? "" : text.substring(nl));
    }

    private void deleteSelected() {
        if (selected == null) return;
        store.delete(selected);
        chats.remove(selected);
        selected = null;
        error = null;
    }

    // ------------------------------------------------------------------ housekeeping

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        chats.addAll(store.loadAll());
    }

    /** Removes chats that were not used for the configured number of days. Runs rarely and is cheap. */
    private void maybePurge() {
        long now = System.currentTimeMillis();
        if (lastPurge != 0 && now - lastPurge < PURGE_INTERVAL_MS) return;
        lastPurge = now;
        try {
            retentionDays = AiConfig.loadOrCreate(configDir).chatRetentionDays;
        } catch (Exception ignored) {
            // keep the previous value
        }
        List<Chat> removed = store.purge(chats, retentionDays, selected);
        if (!removed.isEmpty()) {
            chats.removeAll(removed);
            LOGGER.info("Deleted {} inactive AI chat(s)", removed.size());
        }
    }

    /** Reads config.json again at most every two seconds, to know the models and to keep a valid choice. */
    private void refreshConfig() {
        long now = System.currentTimeMillis();
        if (cachedConfig != null && now - configLoadedAt < 2000) return;
        configLoadedAt = now;
        try {
            cachedConfig = AiConfig.loadOrCreate(configDir);
            retentionDays = cachedConfig.chatRetentionDays;
            Tr.setLanguage(cachedConfig.language);
        } catch (Exception ignored) {
            // keep the previous settings while the file is being edited
        }
        if (cachedConfig != null && cachedConfig.hasModelChoice() && cachedConfig.profileNamed(selectedModel) == null) {
            ModelProfile fallback = cachedConfig.defaultProfile();
            selectedModel = fallback == null ? "" : fallback.name();
        }
    }

    /** Remembers whether the window is open and the text size, at most once per second. */
    private void persistUi() {
        long now = System.currentTimeMillis();
        if (now - lastUiSave < 1000) return;
        if (open.get() == savedOpen && fontScale[0] == savedScale && selectedModel.equals(savedModel)
                && modern == savedModern && sidebarOpen == savedSidebar && updatePreset == savedUpdatePreset) return;
        lastUiSave = now;
        savedOpen = open.get();
        savedScale = fontScale[0];
        savedModel = selectedModel;
        savedModern = modern;
        savedSidebar = sidebarOpen;
        savedUpdatePreset = updatePreset;
        UiState ui = new UiState();
        ui.open = savedOpen;
        ui.fontScale = savedScale;
        ui.model = savedModel;
        ui.modern = savedModern;
        ui.sidebar = savedSidebar;
        ui.updatePreset = savedUpdatePreset;
        ui.save(uiFile);
    }

    private void saveQuietly(Chat chat) {
        try {
            store.save(chat);
        } catch (Exception e) {
            LOGGER.warn("Could not save chat", e);
        }
    }

    // ------------------------------------------------------------------ small helpers

    /** ImGui child windows have their own text scale, so it is applied at the start of every child. */
    private void applyScale() {
        ImGui.setWindowFontScale(fontScale[0]);
    }

    private static void wrapped(String text) {
        ImGui.pushTextWrapPos();
        ImGui.textUnformatted(text);
        ImGui.popTextWrapPos();
    }

    private static String planValue(String text, String key) {
        String prefix = "-- " + key + ":";
        for (String line : text.split("\n", 14)) {
            String t = line.trim();
            if (t.startsWith(prefix)) return t.substring(prefix.length()).trim();
        }
        return "";
    }

    private static String withoutNameLine(String text) {
        if (text.startsWith("-- NAME:")) {
            int nl = text.indexOf('\n');
            return nl < 0 ? "" : text.substring(nl + 1).stripTrailing();
        }
        return text.stripTrailing();
    }

    private static int countLines(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        return lines;
    }

    private static String age(long timestamp) {
        long minutes = Math.max(0, (System.currentTimeMillis() - timestamp) / 60_000L);
        if (minutes < 1) return "now";
        if (minutes < 60) return minutes + " min";
        long hours = minutes / 60;
        if (hours < 48) return hours + " h";
        return (hours / 24) + " d";
    }

    private static String describe(Throwable t) {
        if (t instanceof ConnectException || t instanceof UnknownHostException) {
            return Tr.t("Could not connect to the AI server. Check your internet connection.");
        }
        if (t instanceof HttpTimeoutException) {
            return Tr.t("The AI server took too long to answer. Try again.");
        }
        String message = t.getMessage();
        return message == null || message.isBlank() ? t.getClass().getSimpleName() : Tr.translateKnown(message);
    }
}
