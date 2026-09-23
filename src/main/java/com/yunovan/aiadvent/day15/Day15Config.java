package com.yunovan.aiadvent.day15;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day15Config {

    private final Day15Properties properties;

    public Day15Config(Day15Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day15TaskStore day15TaskStore() {
        return new Day15TaskStore(Path.of(properties.taskDir()));
    }
}