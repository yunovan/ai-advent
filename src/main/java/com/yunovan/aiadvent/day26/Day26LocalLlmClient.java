package com.yunovan.aiadvent.day26;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        List<Day26ChatMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new Day26ChatMessage("system", systemPrompt.trim()));
        }
        messages.add(new Day26ChatMessage("user", prompt.trim()));
        return chat(messages);
    }

    public Day26Answer chat(List<Day26ChatMessage> messages) {
        return chat(messages, null);
    }

    public Day26Answer chat(List<Day26ChatMessage> messages, Day26ChatOptions options) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("Список сообщений к локальной LLM не может быть пустым");
        }
        List<Message> payload = new ArrayList<>();
        String prompt = "";
        for (Day26ChatMessage item : messages) {
            if (item == null || item.role() == null || item.role().isBlank()
                    || item.content() == null || item.content().isBlank()) {
                throw new IllegalArgumentException(
                        "Каждое сообщение к локальной LLM должно иметь роль и непустой текст");
            }
            payload.add(new Message(item.role().trim(), item.content().trim()));
            if ("user".equals(item.role().trim())) {
                prompt = item.content().trim();
            }
        }
        if (prompt.isEmpty()) {
            prompt = payload.getLast().content();
        }
        ChatRequest request = new ChatRequest(
                properties.model(), payload, false, requestOptions(options));

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

    public Day26LoadedModel loadedModel() {
        try {
            PsResponse response = restClient.get()
                    .uri("/api/ps")
                    .retrieve()
                    .body(PsResponse.class);
            if (response == null || response.models() == null || response.models().isEmpty()) {
                return null;
            }
            PsModel model = response.models().getFirst();
            return new Day26LoadedModel(nullSafe(model.name()), model.size(), model.sizeVram());
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    public Day26ModelInfo modelInfo() {
        try {
            ShowResponse response = restClient.post()
                    .uri("/api/show")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ShowRequest(properties.model()))
                    .retrieve()
                    .body(ShowResponse.class);
            if (response == null || response.details() == null) {
                return Day26ModelInfo.unavailable("Ollama не вернула описание модели");
            }
            Map<String, Object> info = response.modelInfo() == null ? Map.of() : response.modelInfo();
            return new Day26ModelInfo(properties.model(),
                    response.details().format() == null ? "" : response.details().format(),
                    nullSafe(response.details().parameterSize()),
                    nullSafe(response.details().quantizationLevel()),
                    longValue(info.get("general.parameter_count")),
                    contextLength(info), "");
        } catch (RestClientException ex) {
            return Day26ModelInfo.unavailable(unavailable(ex).getMessage());
        }
    }

    private Options requestOptions(Day26ChatOptions options) {
        if (options == null) {
            return new Options(properties.maxTokens(), properties.temperature(), null);
        }
        return new Options(options.maxTokens(), options.temperature(), options.numCtx());
    }

    private static long contextLength(Map<String, Object> info) {
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            if (entry.getKey() != null && entry.getKey().endsWith(".context_length")) {
                return longValue(entry.getValue());
            }
        }
        return 0;
    }

    private static long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0;
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

    private record Details(String format,
                           @JsonProperty("parameter_size") String parameterSize,
                           @JsonProperty("quantization_level") String quantizationLevel) {
    }

    private record ChatRequest(String model, List<Message> messages, boolean stream, Options options) {
    }

    private record Message(String role, String content) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Options(@JsonProperty("num_predict") int numPredict,
                           @JsonProperty("temperature") double temperature,
                           @JsonProperty("num_ctx") Integer numCtx) {
    }

    private record ShowRequest(String model) {
    }

    private record ShowResponse(Details details,
                                @JsonProperty("model_info") Map<String, Object> modelInfo) {
    }

    private record PsResponse(List<PsModel> models) {
    }

    private record PsModel(String name, long size,
                           @JsonProperty("size_vram") long sizeVram) {
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
