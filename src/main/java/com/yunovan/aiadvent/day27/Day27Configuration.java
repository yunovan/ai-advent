package com.yunovan.aiadvent.day27;

import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(Day27Properties.class)
public class Day27Configuration {

    @Bean
    public Day27ChatSessionStore day27ChatSessionStore(Day27Properties properties) {
        return new Day27ChatSessionStore(Path.of(properties.storeDir()), properties.maxSessions());
    }
}
