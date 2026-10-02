package com.yunovan.aiadvent.day25;

import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Day25Properties.class)
public class Day25ChatConfiguration {

    @Bean
    public Day25ChatSessionStore day25ChatSessionStore(Day25Properties properties) {
        return new Day25ChatSessionStore(Path.of(properties.storeDir()), properties.maxSessions());
    }
}
