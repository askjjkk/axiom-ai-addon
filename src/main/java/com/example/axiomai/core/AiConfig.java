package com.example.axiomai.core;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Settings stored in config.json inside the addon's config folder. */
public final class AiConfig {
    public static final String ROOT_CERT = "russian_trusted_root_ca_pem.crt";
    public static final String SUB_CERT = "russian_trusted_sub_ca_pem.crt";
    private static final String PLACEHOLDER = "PASTE_YOUR_AUTHORIZATION_KEY_HERE";

    private static final String TEMPLATE = """
            {
              "provider": "gigachat",

              "gigachatAuthKey": "PASTE_YOUR_AUTHORIZATION_KEY_HERE",
              "gigachatScope": "GIGACHAT_API_PERS",
              "gigachatModel": "GigaChat-2-Pro",

              "openaiBaseUrl": "https://api.deepseek.com",
              "openaiApiKey": "",
              "openaiModel": "deepseek-chat",
              "openaiTemperature": 0.3,

              "chatRetentionDays": 3,

              "autoFixAttempts": 2
            }
            """;

    public String provider = "gigachat";
    public String gigachatAuthKey = PLACEHOLDER;
    public String gigachatScope = "GIGACHAT_API_PERS";
    public String gigachatModel = "GigaChat-2-Pro";
    public String openaiBaseUrl = "https://api.deepseek.com";
    public String openaiApiKey = "";
    public String openaiModel = "deepseek-chat";
    /** Randomness of the model. A negative number means: do not send it (some reasoning models refuse it). */
    public double openaiTemperature = 0.3;
    /** Models the user can pick in the chat window (only used with provider "openai"). */
    public List<ModelProfile> models = new ArrayList<>();
    public String defaultModel = "";
    /** Name of a stronger model: it is asked once if the chosen model's brush still fails the automatic test. */
    public String escalateTo = "";
    /** Language of the chat window: "auto" (follows the computer), "ru" or "en". */
    public String language = "auto";
    /** Chats without activity for this many days are deleted automatically (0 = never). */
    public int chatRetentionDays = 3;
    /** How many times the AI may repair a brush that failed the automatic test (0 = no test repair). */
    public int autoFixAttempts = 2;

    /** Reads config.json from the folder; creates a template there on first run. */
    public static AiConfig loadOrCreate(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("config.json");
        if (!Files.exists(file)) {
            Files.writeString(file, TEMPLATE, StandardCharsets.UTF_8);
        }
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') text = text.substring(1); // Notepad adds this invisible mark
        Map<String, Object> json;
        try {
            json = MiniJson.parseObject(text);
        } catch (IllegalArgumentException e) {
            throw new IOException("config.json is broken (" + e.getMessage() + "). Check quotes and commas, "
                    + "or delete the file and it will be created again: " + file, e);
        }
        AiConfig c = new AiConfig();
        c.provider = str(json, "provider", c.provider);
        c.gigachatAuthKey = str(json, "gigachatAuthKey", c.gigachatAuthKey);
        c.gigachatScope = str(json, "gigachatScope", c.gigachatScope);
        c.gigachatModel = str(json, "gigachatModel", c.gigachatModel);
        c.openaiBaseUrl = str(json, "openaiBaseUrl", c.openaiBaseUrl);
        c.openaiApiKey = str(json, "openaiApiKey", c.openaiApiKey);
        c.openaiModel = str(json, "openaiModel", c.openaiModel);
        c.language = str(json, "language", c.language);
        c.defaultModel = str(json, "defaultModel", c.defaultModel);
        c.escalateTo = str(json, "escalateTo", c.escalateTo);
        if (json.get("models") instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object name = m.get("name");
                Object model = m.get("model");
                if (!(name instanceof String) || !(model instanceof String)) continue;
                c.models.add(new ModelProfile((String) name, (String) model,
                        m.get("priceIn") instanceof Number n ? n.doubleValue() : 0,
                        m.get("priceOut") instanceof Number n ? n.doubleValue() : 0,
                        m.get("autoFix") instanceof Number n ? Math.max(-1, Math.min(5, n.intValue())) : -1));
            }
        }
        Object temperature = json.get("openaiTemperature");
        if (temperature instanceof Number) c.openaiTemperature = ((Number) temperature).doubleValue();
        Object days = json.get("chatRetentionDays");
        if (days instanceof Number) c.chatRetentionDays = ((Number) days).intValue();
        Object fixes = json.get("autoFixAttempts");
        if (fixes instanceof Number) c.autoFixAttempts = Math.max(0, Math.min(5, ((Number) fixes).intValue()));
        return c;
    }

    private static String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String ? (String) v : def;
    }

    /** Creates the right client for the chosen provider, or throws an understandable message. */
    public LlmClient createClient(Path dir) throws Exception {
        if ("openai".equalsIgnoreCase(provider)) {
            if (openaiBaseUrl.isBlank()) throw new IllegalStateException("config.json: openaiBaseUrl is empty.");
            return new OpenAiCompatClient(openaiBaseUrl, openaiApiKey, openaiModel, openaiTemperature);
        }
        if (gigachatAuthKey == null || gigachatAuthKey.isBlank() || gigachatAuthKey.contains(PLACEHOLDER)) {
            throw new IllegalStateException("Open " + dir.resolve("config.json")
                    + " and replace " + PLACEHOLDER + " with your GigaChat authorization key.");
        }
        Path root = dir.resolve(ROOT_CERT);
        Path sub = dir.resolve(SUB_CERT);
        for (Path p : List.of(root, sub)) {
            if (!Files.exists(p)) throw new IllegalStateException("Certificate file is missing: " + p);
        }
        SSLContext ssl = GigaChatClient.sslContextFromPem(List.of(root, sub));
        return new GigaChatClient(gigachatAuthKey, gigachatScope, gigachatModel, ssl);
    }

    // ------------------------------------------------------------------ model choice

    /** True when the chat window should offer a model picker. */
    public boolean hasModelChoice() {
        return "openai".equalsIgnoreCase(provider) && !models.isEmpty();
    }

    public ModelProfile profileNamed(String name) {
        if (name != null) {
            for (ModelProfile m : models) if (m.name().equals(name)) return m;
        }
        return null;
    }

    public ModelProfile defaultProfile() {
        ModelProfile byName = profileNamed(defaultModel);
        if (byName != null) return byName;
        return models.isEmpty() ? null : models.get(0);
    }

    /** How many repairs the chosen model may make. */
    public int fixesFor(ModelProfile profile) {
        return profile != null && profile.autoFix() >= 0 ? profile.autoFix() : autoFixAttempts;
    }

    /** The engine for the chosen model (or the plain configured client when no models are listed). */
    public BrushGenerator.Engine createEngine(Path dir, ModelProfile profile) throws Exception {
        if (profile == null || !"openai".equalsIgnoreCase(provider)) {
            return BrushGenerator.Engine.plain(createClient(dir));
        }
        if (openaiBaseUrl.isBlank()) throw new IllegalStateException("config.json: openaiBaseUrl is empty.");
        LlmClient client = new OpenAiCompatClient(openaiBaseUrl, openaiApiKey, profile.model(), openaiTemperature);
        return new BrushGenerator.Engine(client, profile.name(), profile.priceIn(), profile.priceOut());
    }

    /** The stronger fallback engine, or null when none is configured or it is the chosen model itself. */
    public BrushGenerator.Engine createEscalationEngine(Path dir, ModelProfile chosen) throws Exception {
        if (!hasModelChoice() || escalateTo == null || escalateTo.isBlank()) return null;
        ModelProfile stronger = profileNamed(escalateTo);
        if (stronger == null || (chosen != null && stronger.name().equals(chosen.name()))) return null;
        return createEngine(dir, stronger);
    }
}
