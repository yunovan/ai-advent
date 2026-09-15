package com.yunovan.aiadvent.day12;

import com.yunovan.aiadvent.day11.Day11FileMemoryStore;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day12MemoryStoreConfig {

    private final Day12Properties properties;

    public Day12MemoryStoreConfig(Day12Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day11FileMemoryStore day12MemoryStore() {
        return new Day11FileMemoryStore(Path.of(properties.memoryDir()));
    }

    @Bean
    public Day12DialogStore day12DialogStore() {
        return new Day12DialogStore(Path.of(properties.dialogDir()));
    }

    @Bean
    public Day12ProfileStore day12ProfileStore() {
        return new Day12ProfileStore(Path.of(properties.profileDir()));
    }

    @Bean
    public Day12DialogProfileStore day12DialogProfileStore() {
        return new Day12DialogProfileStore(Path.of(properties.profileDir(), "links"));
    }
}