package com.example.axiomai.core;

import java.io.IOException;
import java.util.List;

/** Anything that can answer a prompt: GigaChat, DeepSeek, a local Ollama... */
public interface LlmClient {
    /** Sends the whole conversation (older messages first) and returns the model's answer. */
    String complete(String systemPrompt, List<ChatMessage> history) throws Exception;

    default String complete(String systemPrompt, String userPrompt) throws Exception {
        return complete(systemPrompt, List.of(ChatMessage.user(userPrompt)));
    }

    /** Tokens used by one request, as reported by the server. */
    record Usage(long promptTokens, long completionTokens) {
        public static final Usage NONE = new Usage(0, 0);
    }

    /** Usage of the most recent call of this client (NONE if the server did not report it). */
    default Usage usage() {
        return Usage.NONE;
    }

    /** Reads usage.prompt_tokens and usage.completion_tokens from a server answer. */
    static Usage parseUsage(String body) {
        try {
            Object json = MiniJson.parse(body);
            Object in = MiniJson.get(json, "usage", "prompt_tokens");
            Object out = MiniJson.get(json, "usage", "completion_tokens");
            return new Usage(in instanceof Number n ? n.longValue() : 0, out instanceof Number n ? n.longValue() : 0);
        } catch (RuntimeException e) {
            return Usage.NONE;
        }
    }

    /** How many of the latest messages are sent to the model (keeps requests small). */
    int MAX_HISTORY = 12;

    /** Request body with the default temperature. */
    static String buildBody(String model, String system, List<ChatMessage> history) {
        return buildBody(model, system, history, 0.3);
    }

    /** Request body in the common "chat completions" format. A negative temperature leaves the parameter out. */
    static String buildBody(String model, String system, List<ChatMessage> history, double temperature) {
        int from = Math.max(0, history.size() - MAX_HISTORY);
        while (from < history.size() - 1 && !history.get(from).isUser()) from++; // start with a user message
        StringBuilder b = new StringBuilder();
        b.append("{\"model\":").append(MiniJson.quote(model));
        if (temperature >= 0) b.append(",\"temperature\":").append(temperature);
        b.append(",\"stream\":false,\"messages\":[")
                .append("{\"role\":\"system\",\"content\":").append(MiniJson.quote(system)).append("}");
        for (int i = from; i < history.size(); i++) {
            ChatMessage m = history.get(i);
            // two answers in a row (after "regenerate"): only the newer one is sent, roles must alternate
            if (!m.isUser() && i + 1 < history.size() && !history.get(i + 1).isUser()) continue;
            b.append(",{\"role\":").append(MiniJson.quote(m.isUser() ? "user" : "assistant"))
                    .append(",\"content\":").append(MiniJson.quote(m.text())).append("}");
        }
        return b.append("]}").toString();
    }

    /** Pulls choices[0].message.content out of the server answer, or explains what went wrong. */
    static String extractAnswer(int status, String body) throws IOException {
        if (status < 200 || status >= 300) {
            throw new IOException(explain(status) + " (HTTP " + status + ": " + shorten(body) + ")");
        }
        Object content;
        try {
            content = MiniJson.get(MiniJson.parse(body), "choices", 0, "message", "content");
        } catch (IllegalArgumentException e) {
            throw new IOException("Server sent something that is not valid JSON: " + shorten(body));
        }
        if (!(content instanceof String) || ((String) content).isBlank()) {
            throw new IOException("The model returned an empty answer: " + shorten(body));
        }
        return (String) content;
    }

    /** A human explanation for the most common HTTP errors. */
    static String explain(int status) {
        return switch (status) {
            case 401, 403 -> "The AI server rejected the key or the access. Check openaiApiKey in config.json "
                    + "and that the key has access to the service";
            case 402 -> "The AI account has no money for this request. Top up the balance";
            case 404 -> "Address or model not found. Check openaiBaseUrl and openaiModel in config.json";
            case 429 -> "Too many requests or a limit was reached. Wait a minute and try again";
            case 500, 502, 503, 504 -> "The AI server has a temporary problem (it was already retried automatically). "
                    + "Press Retry in a minute, or choose another model in config.json";
            default -> "The AI server returned an error";
        };
    }

    static String shorten(String text) {
        if (text == null) return "";
        String t = text.strip();
        return t.length() > 400 ? t.substring(0, 400) + "..." : t;
    }
}
