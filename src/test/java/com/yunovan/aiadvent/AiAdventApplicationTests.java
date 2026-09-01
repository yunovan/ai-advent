package com.yunovan.aiadvent;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.llm.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AiAdventApplicationTests {

    @Autowired
    private LlmClient llmClient;

    @Test
    void contextLoadsLlmClient() {
        assertThat(llmClient).isNotNull();
    }
}
