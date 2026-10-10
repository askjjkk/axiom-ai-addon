package com.example.axiomai.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Small window settings that survive a restart of the game: is the chat window open, and the text size. */
public final class UiState {
    public boolean open = true;
    public float fontScale = 1.0f;
    public String model = "";
    public boolean modern = true;
    public boolean sidebar = true;
    /** When a chat refines its brush, the same preset is changed (true) instead of saving a new one. */
    public boolean updatePreset = true;

    public static UiState load(Path file) {
        UiState state = new UiState();
        try {
            if (Files.exists(file)) {
                Map<String, Object> json = MiniJson.parseObject(Files.readString(file, StandardCharsets.UTF_8));
                if (json.get("open") instanceof Boolean b) state.open = b;
                if (json.get("model") instanceof String m) state.model = m;
                if (json.get("modern") instanceof Boolean b) state.modern = b;
                if (json.get("sidebar") instanceof Boolean b) state.sidebar = b;
                if (json.get("updatePreset") instanceof Boolean b) state.updatePreset = b;
                if (json.get("fontScale") instanceof Number n) {
                    state.fontScale = Math.max(0.8f, Math.min(1.8f, n.floatValue()));
                }
            }
        } catch (Exception ignored) {
            // broken file: use the defaults
        }
        return state;
    }

    public void save(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "{\"open\": " + open + ", \"fontScale\": " + fontScale
                    + ", \"model\": " + MiniJson.quote(model) + ", \"modern\": " + modern
                    + ", \"sidebar\": " + sidebar + ", \"updatePreset\": " + updatePreset + "}\n", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // not important enough to disturb the user
        }
    }
}
