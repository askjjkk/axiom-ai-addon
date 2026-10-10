package com.example.axiomai.core;

/**
 * One message in a chat. role is "user" or "assistant".
 * preset: saved preset name. note: remark of the automatic check. meta: model, tokens and cost (assistant only).
 * updated: true when this answer changed the chat's existing preset instead of saving a new one.
 */
public record ChatMessage(String role, String text, long time, String preset, String note, String meta, boolean updated) {
    public ChatMessage(String role, String text, long time, String preset, String note, String meta) {
        this(role, text, time, preset, note, meta, false);
    }

    public ChatMessage(String role, String text, long time, String preset, String note) {
        this(role, text, time, preset, note, "", false);
    }

    public ChatMessage(String role, String text, long time, String preset) {
        this(role, text, time, preset, "", "", false);
    }

    public static ChatMessage user(String text) {
        return new ChatMessage("user", text, System.currentTimeMillis(), "", "", "", false);
    }

    public static ChatMessage assistant(String text, String preset) {
        return assistant(text, preset, "", "", false);
    }

    public static ChatMessage assistant(String text, String preset, String note) {
        return assistant(text, preset, note, "", false);
    }

    public static ChatMessage assistant(String text, String preset, String note, String meta) {
        return assistant(text, preset, note, meta, false);
    }

    public static ChatMessage assistant(String text, String preset, String note, String meta, boolean updated) {
        return new ChatMessage("assistant", text, System.currentTimeMillis(),
                preset == null ? "" : preset, note == null ? "" : note, meta == null ? "" : meta, updated);
    }

    /** The same message with another text and preset name (used when a preset is renamed). */
    public ChatMessage withTextAndPreset(String newText, String newPreset) {
        return new ChatMessage(role, newText, time, newPreset, note, meta, updated);
    }

    public boolean isUser() {
        return "user".equals(role);
    }
}
