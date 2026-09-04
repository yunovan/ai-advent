package com.yunovan.aiadvent;

import com.yunovan.aiadvent.day05.Day5Properties;
import com.yunovan.aiadvent.llm.LlmHttpProperties;
import com.yunovan.aiadvent.llm.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({LlmProperties.class, LlmHttpProperties.class, Day5Properties.class})
public class AiAdventApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAdventApplication.class, args);
    }
}
