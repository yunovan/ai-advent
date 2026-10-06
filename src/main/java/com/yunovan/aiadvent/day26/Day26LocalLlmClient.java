package com.yunovan.aiadvent.day26;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class Day26LocalLlmClient {

    private final Day26Properties properties;
    private final RestClient restClient;

    public Day26LocalLlmClient(RestClient.Builder restClientBuilder, Day26Properties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.endpoint())
                .requestFactory(requestFactory(properties))
                .build();
    }

    public String version() {
        try {
            VersionResponse response = restClient.get()
                    .uri("/api/version")
                    .retrieve()
                    .body(VersionResponse.class);
            return response == null || response.version() == null ? "" : response.version();
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    public List<Day26InstalledModel> models() {
        try {
            TagsResponse response = restClient.get()
                    .uri("/api/tags")
                    .retrieve()
                    .body(TagsResponse.class);
            if (response == null || response.models() == null) {
                return List.of();
            }
            return response.models().stream()
                    .map(tag -> new Day26InstalledModel(
                            tag.name(), tag.size(),
                            tag.details() == null ? "" : nullSafe(tag.details().parameterSize()),
                            tag.details() == null ? "" : nullSafe(tag.details().quantizationLevel())))
                    .toList();
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    public Day26Answer chat(String prompt) {
        return chat("", prompt);
    }

    public Day26Answer chat(String systemPrompt, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Запрос к локальной LLM не может быть пустым");
        }
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Message("system", systemPrompt.trim()));
        }
        messages.add(new Message("user", prompt.trim()));
        ChatRequest request = new ChatRequest(
                properties.model(), messages, false,
                new Options(properties.maxTokens(), properties.temperature()));

        long started = System.nanoTime();
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException ex) {
            throw new Day26LlmException(
                    "Ошибка HTTP %d от %s: %s"
                            .formatted(ex.getStatusCode().value(), properties.endpoint(), reason(ex)));
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
        long elapsedMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        if (response == null || response.message() == null || response.message().content() == null) {
            throw new Day26LlmException(
                    "Локальный LLM %s вернул пустой ответ с %s"
                            .formatted(properties.model(), properties.endpoint()));
        }
        return new Day26Answer(
                prompt.trim(),
                response.message().content().trim(),
                properties.model(),
                properties.endpoint(),
                elapsedMs,
                (int) response.promptEvalCount(),
                (int) response.evalCount(),
                tokensPerSecond(response));
    }

    private Day26LlmException unavailable(RestClientException ex) {
        String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return new Day26LlmException(
                "Локальный LLM недоступен на %s (%s)".formatted(properties.endpoint(), detail));
    }

    private static double tokensPerSecond(ChatResponse response) {
        if (response.evalCount() <= 0 || response.evalDuration() <= 0) {
            return 0.0;
        }
        double seconds = response.evalDuration() / 1_000_000_000.0;
        return seconds <= 0 ? 0.0 : Math.round(response.evalCount() / seconds * 10.0) / 10.0;
    }

    private static String reason(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return body == null || body.isBlank() ? ex.getStatusText() : body;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static ClientHttpRequestFactory requestFactory(Day26Properties properties) {
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return factory;
    }

    private record VersionResponse(String version) {
    }

    private record TagsResponse(List<TagModel> models) {
    }

    private record TagModel(String name, long size, Details details) {
    }

    private record Details(@JsonProperty("parameter_size") String parameterSize,
                       @JsonProperty("quantization_level") String quantizationLevel) {
    }

    private record ChatRequest(String model, List<Message> messages, boolean stream, Options options) {
    }

    private record Message(String role, String content) {
    }

    private record Options(@JsonProperty("num_predict") int numPredict,
                           @JsonProperty("temperature") double temperature) {
    }

    private record ChatResponse(Message message,
                                @JsonProperty("eval_count") long evalCount,
                                @JsonProperty("prompt_eval_count") long promptEvalCount,
                                @JsonProperty("eval_duration") long evalDuration,
                                @JsonProperty("prompt_eval_duration") long promptEvalDuration,
                                @JsonProperty("load_duration") long loadDuration,
                                @JsonProperty("total_duration") long totalDuration) {
    }
}
