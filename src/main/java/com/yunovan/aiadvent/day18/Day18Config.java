package com.yunovan.aiadvent.day18;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day18Config {

    private final Day18Properties properties;

    public Day18Config(Day18Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day18Store day18Store() {
        return new Day18Store(Path.of(properties.storeDir()).toAbsolutePath().normalize());
    }

    @Bean
    public Day18HttpProbe day18HttpProbe() {
        return new Day18HttpProbe();
    }
}