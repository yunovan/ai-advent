package com.yunovan.aiadvent.day11;

import com.yunovan.aiadvent.agent.dialog.DialogStore;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day11MemoryStoreConfig {

    private final Day11Properties properties;

    public Day11MemoryStoreConfig(Day11Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day11FileMemoryStore day11MemoryStore() {
        return new Day11FileMemoryStore(Path.of(properties.memoryDir()));
    }

    @Bean
    public DialogStore day11DialogStore() {
        return new Day11DialogStore(Path.of(properties.dialogDir()));
    }
}
