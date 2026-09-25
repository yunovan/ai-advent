package com.yunovan.aiadvent.day18;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

public final class Day18HttpProbe {

    private final HttpClient http;

    public Day18HttpProbe() {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public ProbeResult probe(String url) {
        String target = url.trim();
        long start = System.currentTimeMillis();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(target))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;
            String payload = "url=" + target + ", status=" + response.statusCode()
                    + ", bytes=" + response.body().length();
            return new ProbeResult(elapsed, payload);
        } catch (Exception ex) {
            long elapsed = System.currentTimeMillis() - start;
            String payload = "url=" + target + ", status=0, error="
                    + ex.getMessage().replace('\n', ' ').toLowerCase(Locale.ROOT);
            return new ProbeResult(elapsed, payload);
        }
    }

    public record ProbeResult(double valueMs, String payload) {
    }
}