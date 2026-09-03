package com.yunovan.aiadvent.llm;

import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class LlmClient {

    private final RestClient restClient;
    private final LlmProperties properties;

    public LlmClient(RestClient.Builder restClientBuilder, LlmProperties properties) {
        this.properties = properties;
        RestClient.Builder builder = restClientBuilder.baseUrl(properties.baseUrl());
        if (properties.hasApiKey()) {
            builder = builder
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .defaultHeader("HTTP-Referer", "https://github.com/yunovan/ai-advent")
                    .defaultHeader("X-Title", "AI Advent")
                    .defaultHeader("X-OpenRouter-Title", "AI Advent");
        }
        this.restClient = builder.build();
    }

    public String complete(String prompt) {
        return complete(CompletionCommand.unconstrained(prompt)).content();
    }

    public LlmReply complete(CompletionCommand command) {
        if (command == null || command.prompt() == null || command.prompt().isBlank()) {
            throw new LlmException("Prompt must not be blank");
        }
        if (!properties.hasApiKey()) {
            throw new LlmException(
                    "LLM API key is missing. Set LLM_API_KEY or OPENROUTER_API_KEY in .env before sending a request.");
        }

        List<ChatCompletionRequest.Message> messages = new ArrayList<>();
        if (command.systemPrompt() != null && !command.systemPrompt().isBlank()) {
            messages.add(new ChatCompletionRequest.Message("system", command.systemPrompt().trim()));
        }
        messages.add(new ChatCompletionRequest.Message("user", command.prompt().trim()));

        ChatCompletionRequest request = ChatCompletionRequest.of(
                properties.model(), messages, command.maxTokens(), command.stop(), command.temperature());
        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            if (response == null) {
                throw new LlmException("LLM returned an empty response");
            }
            return response.requiredReply();
        } catch (RestClientResponseException ex) {
            throw new LlmException(
                    "LLM API error %s: %s".formatted(ex.getStatusCode().value(), responseBody(ex)), ex);
        } catch (RestClientException ex) {
            throw new LlmException("Failed to call LLM API: " + ex.getMessage(), ex);
        }
    }

    private static String responseBody(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return (body == null || body.isBlank()) ? ex.getStatusText() : body;
    }
}
