package com.example.axiomai.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Works with any server that speaks the OpenAI "chat completions" protocol: DeepSeek, Ollama, OpenRouter... */
public final class OpenAiCompatClient implements LlmClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private volatile Usage lastUsage = Usage.NONE;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public OpenAiCompatClient(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, 0.3);
    }

    /** @param temperature negative = do not send the parameter (needed by some reasoning models) */
    public OpenAiCompatClient(String baseUrl, String apiKey, String model, double temperature) {
        this.temperature = temperature;
        String b = baseUrl == null ? "" : baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        this.baseUrl = b;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> history) throws Exception {
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(180))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        LlmClient.buildBody(model, systemPrompt, history, temperature), StandardCharsets.UTF_8));
        if (!apiKey.isEmpty()) rb.header("Authorization", "Bearer " + apiKey);
        lastUsage = Usage.NONE;
        HttpResponse<String> resp = HttpRetry.send(http, rb.build(), 2);
        String answer = LlmClient.extractAnswer(resp.statusCode(), resp.body());
        lastUsage = LlmClient.parseUsage(resp.body());
        return answer;
    }

    @Override
    public Usage usage() {
        return lastUsage;
    }
}
