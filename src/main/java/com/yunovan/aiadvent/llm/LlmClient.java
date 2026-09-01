package com.yunovan.aiadvent.llm;

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
        if (prompt == null || prompt.isBlank()) {
            throw new LlmException("Prompt must not be blank");
        }
        if (!properties.hasApiKey()) {
            throw new LlmException(
                    "LLM API key is missing. Set LLM_API_KEY or OPENROUTER_API_KEY in .env before sending a request.");
        }

        ChatCompletionRequest request = ChatCompletionRequest.userPrompt(properties.model(), prompt.trim());
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
            return response.requiredContent();
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
