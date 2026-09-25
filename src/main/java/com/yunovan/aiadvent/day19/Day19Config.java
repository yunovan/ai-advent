package com.yunovan.aiadvent.day19;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day19Config {

    private final Day19Properties properties;

    public Day19Config(Day19Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day19SaveService day19SaveService() {
        return new Day19SaveService(properties);
    }
}