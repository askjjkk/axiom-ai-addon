package com.example.axiomai.core;

import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Sends a request again after short pauses when the server only has a temporary problem (502, 503, 504, 429...). */
final class HttpRetry {
    private static final long[] PAUSES_MS = {2_000L, 6_000L};

    private HttpRetry() {
    }

    static boolean isTemporary(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    /** Returns the last response (even a failed one) so the caller can explain it. Timeouts are never repeated. */
    static HttpResponse<String> send(HttpClient http, HttpRequest request, int maxRetries) throws Exception {
        for (int attempt = 0; ; attempt++) {
            try {
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (!isTemporary(response.statusCode()) || attempt >= maxRetries) return response;
            } catch (ConnectException e) {
                if (attempt >= maxRetries) throw e;
            }
            Thread.sleep(PAUSES_MS[Math.min(attempt, PAUSES_MS.length - 1)]);
        }
    }
}
