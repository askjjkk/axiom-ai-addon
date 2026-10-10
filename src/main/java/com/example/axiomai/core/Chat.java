package com.example.axiomai.core;

import java.util.List;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/** A conversation with the AI. Safe to read from the UI thread while the worker thread adds messages. */
public final class Chat {
    public final String id;
    public final long createdAt;
    public volatile long lastActive;
    public volatile boolean deleted;
    /** File name (inside Axiom's script_brush folder) and name of the preset that belongs to this chat. */
    public volatile String presetFile = "";
    public volatile String presetName = "";
    /** A name the user gave to the chat; empty = use the beginning of the first request. */
    public volatile String customTitle = "";
    public final List<ChatMessage> messages = new CopyOnWriteArrayList<>();

    public Chat(String id, long createdAt, long lastActive) {
        this.id = id;
        this.createdAt = createdAt;
        this.lastActive = lastActive;
    }

    public static Chat create() {
        long now = System.currentTimeMillis();
        return new Chat(UUID.randomUUID().toString(), now, now);
    }

    /** Marks the chat as used right now (this is what the "3 days of inactivity" rule looks at). */
    public void touch() {
        lastActive = System.currentTimeMillis();
    }

    public void add(ChatMessage message) {
        messages.add(message);
        touch();
    }

    /** Short title: the beginning of the first request. */
    public String title() {
        String custom = customTitle;
        if (custom != null && !custom.isBlank()) return custom;
        return autoTitle();
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private volatile String autoCache;

    public String autoTitle() {
        String cached = autoCache;
        if (cached != null) return cached;
        for (ChatMessage m : messages) {
            if (m.isUser()) {
                String t = WHITESPACE.matcher(m.text()).replaceAll(" ").trim();
                String title = t.length() > 30 ? t.substring(0, 30).trim() + "..." : t;
                autoCache = title;   // the first request never changes
                return title;
            }
        }
        return "New chat";
    }
}
