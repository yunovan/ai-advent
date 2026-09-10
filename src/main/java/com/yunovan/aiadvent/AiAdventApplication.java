package com.yunovan.aiadvent;

import com.yunovan.aiadvent.day05.Day5Properties;
import com.yunovan.aiadvent.day07.Day7Properties;
import com.yunovan.aiadvent.day08.Day8Properties;
import com.yunovan.aiadvent.day09.Day9Properties;
import com.yunovan.aiadvent.llm.LlmHttpProperties;
import com.yunovan.aiadvent.llm.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    LlmProperties.class,
    LlmHttpProperties.class,
    Day5Properties.class,
    Day7Properties.class,
    Day8Properties.class,
    Day9Properties.class
})
public class AiAdventApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAdventApplication.class, args);
    }
}
