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
@EnableConfigurationProperties(LlmProperties.class)
@TestPropertySource(
        properties = {
            "llm.api-key=test-key",
            "llm.base-url=https://api.openai.com/v1",
            "llm.model=gpt-4o-mini"
        })
class LlmClientTest {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void completeSendsChatCompletionsRequestAndReturnsMessage() {
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().json("""
                        {"model":"gpt-4o-mini","messages":[{"role":"user","content":"Hello"}]}
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
        server.expect(requestTo("https://api.openai.com/v1/chat/completions"))
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
                new LlmClient(RestClient.builder(), new LlmProperties("", "https://api.openai.com/v1", "gpt-4o-mini"));

        assertThatThrownBy(() -> clientWithoutKey.complete("Hello"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("API key");
    }
}
