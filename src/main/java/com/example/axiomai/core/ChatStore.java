package com.example.axiomai.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Keeps every chat as one small JSON file in a folder. */
public final class ChatStore {
    private static final Pattern SAFE_ID = Pattern.compile("[0-9a-fA-F-]{8,40}");
    private final Path dir;

    public ChatStore(Path dir) {
        this.dir = dir;
    }

    /** Loads all readable chats, newest activity first. Broken files are skipped. */
    public synchronized List<Chat> loadAll() {
        List<Chat> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) return out;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : files) {
                try {
                    Chat c = read(p);
                    if (c != null) out.add(c);
                } catch (Exception ignored) {
                    // a broken file must never break the whole history
                }
            }
        } catch (IOException ignored) {
            // no history available
        }
        out.sort(Comparator.comparingLong((Chat c) -> c.lastActive).reversed());
        return out;
    }

    public synchronized void save(Chat chat) throws IOException {
        if (chat.deleted || chat.messages.isEmpty()) return;
        Files.createDirectories(dir);
        Path tmp = dir.resolve(chat.id + ".tmp");
        Files.writeString(tmp, toJson(chat), StandardCharsets.UTF_8);
        Files.move(tmp, file(chat), StandardCopyOption.REPLACE_EXISTING);
    }

    public synchronized void delete(Chat chat) {
        chat.deleted = true;
        try {
            Files.deleteIfExists(file(chat));
        } catch (IOException ignored) {
            // will be retried by the next cleanup
        }
    }

    /**
     * Deletes chats with no activity for more than {@code days} days (0 or less = keep forever).
     * The chat in {@code keep} (the one open right now) is never removed. Returns the removed chats.
     */
    public synchronized List<Chat> purge(List<Chat> chats, long days, Chat keep) {
        List<Chat> removed = new ArrayList<>();
        if (days <= 0) return removed;
        long cutoff = System.currentTimeMillis() - days * 86_400_000L;
        for (Chat c : chats) {
            boolean isKept = keep != null && keep.id.equals(c.id);
            if (!isKept && c.lastActive < cutoff) {
                delete(c);
                removed.add(c);
            }
        }
        return removed;
    }

    private Path file(Chat chat) {
        if (!SAFE_ID.matcher(chat.id).matches()) throw new IllegalStateException("Unsafe chat id");
        return dir.resolve(chat.id + ".json");
    }

    private static String toJson(Chat c) {
        StringBuilder b = new StringBuilder();
        b.append("{\n  \"id\": ").append(MiniJson.quote(c.id))
                .append(",\n  \"createdAt\": ").append(c.createdAt)
                .append(",\n  \"lastActive\": ").append(c.lastActive)
                .append(",\n  \"title\": ").append(MiniJson.quote(c.customTitle == null ? "" : c.customTitle))
                .append(",\n  \"presetFile\": ").append(MiniJson.quote(c.presetFile == null ? "" : c.presetFile))
                .append(",\n  \"presetName\": ").append(MiniJson.quote(c.presetName == null ? "" : c.presetName))
                .append(",\n  \"messages\": [");
        boolean first = true;
        for (ChatMessage m : c.messages) {
            b.append(first ? "\n" : ",\n");
            first = false;
            b.append("    {\"role\": ").append(MiniJson.quote(m.role()))
                    .append(", \"time\": ").append(m.time())
                    .append(", \"preset\": ").append(MiniJson.quote(m.preset()))
                    .append(", \"note\": ").append(MiniJson.quote(m.note()))
                    .append(", \"meta\": ").append(MiniJson.quote(m.meta()))
                    .append(", \"updated\": ").append(m.updated())
                    .append(", \"text\": ").append(MiniJson.quote(m.text())).append("}");
        }
        b.append("\n  ]\n}\n");
        return b.toString();
    }

    private static Chat read(Path p) throws IOException {
        String text = Files.readString(p, StandardCharsets.UTF_8);
        Map<String, Object> json = MiniJson.parseObject(text);
        Object id = json.get("id");
        if (!(id instanceof String) || !SAFE_ID.matcher((String) id).matches()) return null;
        long created = num(json.get("createdAt"), System.currentTimeMillis());
        long last = num(json.get("lastActive"), created);
        Chat chat = new Chat((String) id, created, last);
        if (json.get("title") instanceof String t) chat.customTitle = t;
        if (json.get("presetFile") instanceof String f) chat.presetFile = f;
        if (json.get("presetName") instanceof String n) chat.presetName = n;
        Object msgs = json.get("messages");
        if (msgs instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m)) continue;
                Object role = m.get("role");
                Object body = m.get("text");
                if (!(role instanceof String) || !(body instanceof String)) continue;
                Object preset = m.get("preset");
                Object note = m.get("note");
                Object meta = m.get("meta");
                chat.messages.add(new ChatMessage((String) role, (String) body,
                        num(m.get("time"), created), preset instanceof String ? (String) preset : "",
                        note instanceof String ? (String) note : "",
                        meta instanceof String ? (String) meta : "",
                        Boolean.TRUE.equals(m.get("updated"))));
            }
        }
        return chat.messages.isEmpty() ? null : chat;
    }

    private static long num(Object o, long def) {
        return o instanceof Number ? ((Number) o).longValue() : def;
    }
}
