package com.yunovan.aiadvent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

@RestClientTest(LlmClient.class)
@EnableConfigurationProperties({LlmProperties.class, LlmHttpProperties.class})
@TestPropertySource(
        properties = {
            "llm.api-key=test-key",
            "llm.base-url=https://openrouter.ai/api/v1",
            "llm.model=openai/gpt-4o-mini",
            "llm.http.customize-client=false",
            "llm.http.max-attempts=1"
        })
class LlmClientTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void completeSendsChatCompletionsRequestAndReturnsMessage() {
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(header("HTTP-Referer", "https://github.com/yunovan/ai-advent"))
                .andExpect(content().json("""
                        {"model":"openai/gpt-4o-mini","messages":[{"role":"user","content":"Hello"}]}
                        """))
                .andRespond(withSuccess(
                        """
                        {
                          "id": "chatcmpl-1",
                          "object": "chat.completion",
                          "choices": [
                            {"index": 0, "message": {"role": "assistant", "content": "Hi from the model"}}
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        String answer = llmClient.complete("Hello");

        assertThat(answer).isEqualTo("Hi from the model");
        server.verify();
    }

    @Test
    void completeWrapsHttpError() {
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"bad key\"}}"));

        assertThatThrownBy(() -> llmClient.complete("Hello"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("bad key");
    }

    @Test
    void completeRejectsBlankPrompt() {
        assertThatThrownBy(() -> llmClient.complete("  "))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Prompt");
    }

    @Test
    void completeRejectsMissingApiKey() {
        LlmClient clientWithoutKey =
                new LlmClient(
                        RestClient.builder(),
                        new LlmProperties("", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
                        LlmHttpProperties.disabled());

        assertThatThrownBy(() -> clientWithoutKey.complete("Hello"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void completeSendsFormatLengthAndStopControls() {
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model":"openai/gpt-4o-mini",
                          "messages":[
                            {"role":"system","content":"Be short."},
                            {"role":"user","content":"Hello"}
                          ],
                          "max_tokens":80,
                          "stop":["<<<END>>>"]
                        }
                        """))
                .andRespond(withSuccess(
                        """
                        {
                          "choices": [
                            {
                              "finish_reason": "stop",
                              "message": {"role": "assistant", "content": "1. One"}
                            }
                          ]
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        LlmReply reply = llmClient.complete(
                new CompletionCommand("Hello", "Be short.", 80, List.of("<<<END>>>")));

        assertThat(reply.content()).isEqualTo("1. One");
        assertThat(reply.finishReason()).isEqualTo("stop");
        server.verify();
    }

    @Test
    void completeSendsTemperature() {
        server.expect(requestTo("https://openrouter.ai/api/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(content().json("""
                        {
                          "model":"openai/gpt-4o-mini",
                          "messages":[{"role":"user","content":"Hello"}],
                          "temperature":0.7
                        }
                        """))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"role":"assistant","content":"Hi"},"finish_reason":"stop"}]}
                        """,
                        MediaType.APPLICATION_JSON));

        LlmReply reply = llmClient.complete(CompletionCommand.withTemperature("Hello", 0.7));

        assertThat(reply.content()).isEqualTo("Hi");
        server.verify();
    }
}
