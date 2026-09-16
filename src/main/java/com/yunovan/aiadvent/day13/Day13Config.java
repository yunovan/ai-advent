package com.yunovan.aiadvent.day13;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day13Config {

    private final Day13Properties properties;

    public Day13Config(Day13Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day13TaskStore day13TaskStore() {
        return new Day13TaskStore(Path.of(properties.taskDir()));
    }
}