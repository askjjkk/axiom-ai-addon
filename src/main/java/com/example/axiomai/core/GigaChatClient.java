package com.example.axiomai.core;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GigaChat (Sber): first exchanges the authorization key for a short-lived token, then asks the model. */
public final class GigaChatClient implements LlmClient {
    private static final String DEFAULT_OAUTH = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth";
    private static final String DEFAULT_API = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions";

    private final String authKey;
    private final String scope;
    private final String model;
    private final String oauthUrl;
    private final String apiUrl;
    private final HttpClient http;

    private volatile Usage lastUsage = Usage.NONE;
    private String token;
    private long tokenExpiresAtMs;

    public GigaChatClient(String authKey, String scope, String model, SSLContext ssl) {
        this(authKey, scope, model, ssl, DEFAULT_OAUTH, DEFAULT_API);
    }

    GigaChatClient(String authKey, String scope, String model, SSLContext ssl, String oauthUrl, String apiUrl) {
        String k = authKey.trim();
        if (k.regionMatches(true, 0, "Basic ", 0, 6)) k = k.substring(6).trim();
        this.authKey = k;
        this.scope = scope;
        this.model = model;
        this.oauthUrl = oauthUrl;
        this.apiUrl = apiUrl;
        HttpClient.Builder b = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15));
        if (ssl != null) b.sslContext(ssl);
        this.http = b.build();
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> history) throws Exception {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(180))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + token())
                    .POST(HttpRequest.BodyPublishers.ofString(
                            LlmClient.buildBody(model, systemPrompt, history), StandardCharsets.UTF_8))
                    .build();
            lastUsage = Usage.NONE;
            HttpResponse<String> resp = HttpRetry.send(http, req, 2);
            String answer = LlmClient.extractAnswer(resp.statusCode(), resp.body());
            lastUsage = LlmClient.parseUsage(resp.body());
            return answer;
        } catch (SSLException e) {
            throw new IOException("TLS error while talking to GigaChat. Check that both .crt files "
                    + "(root and sub) are in the config folder and are valid. Details: " + e.getMessage(), e);
        }
    }

    @Override
    public Usage usage() {
        return lastUsage;
    }

    private synchronized String token() throws Exception {
        if (token != null && System.currentTimeMillis() < tokenExpiresAtMs - 60_000) return token;
        HttpRequest req = HttpRequest.newBuilder(URI.create(oauthUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .header("RqUID", UUID.randomUUID().toString())
                .header("Authorization", "Basic " + authKey)
                .POST(HttpRequest.BodyPublishers.ofString("scope=" + scope, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) {
            throw new IOException("GigaChat refused the authorization key (HTTP " + resp.statusCode() + "): "
                    + LlmClient.shorten(resp.body()));
        }
        Map<String, Object> json = MiniJson.parseObject(resp.body());
        Object t = json.get("access_token");
        if (!(t instanceof String)) throw new IOException("GigaChat did not return an access token.");
        token = (String) t;
        Object exp = json.get("expires_at");
        tokenExpiresAtMs = exp instanceof Number ? ((Number) exp).longValue() : System.currentTimeMillis() + 25 * 60_000L;
        return token;
    }

    /** Builds an SSL context that trusts the given PEM certificates (the Russian root and sub CA). */
    public static SSLContext sslContextFromPem(List<Path> pemFiles) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        int n = 0;
        for (Path p : pemFiles) {
            try (InputStream in = Files.newInputStream(p)) {
                for (Certificate c : cf.generateCertificates(in)) ks.setCertificateEntry("ru-ca-" + (n++), c);
            }
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }
}
