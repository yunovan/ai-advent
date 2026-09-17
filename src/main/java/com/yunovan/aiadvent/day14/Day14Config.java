package com.yunovan.aiadvent.day14;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day14Config {

    private final Day14Properties properties;

    public Day14Config(Day14Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day14InvariantStore day14InvariantStore() {
        return new Day14InvariantStore(Path.of(properties.invariantDir()));
    }
}