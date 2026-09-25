package com.yunovan.aiadvent.day17;

import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Day17Config {

    private final Day17Properties properties;

    public Day17Config(Day17Properties properties) {
        this.properties = properties;
    }

    @Bean
    public Day17TicketStore day17TicketStore() {
        return new Day17TicketStore(Path.of(properties.storeDir()).toAbsolutePath().normalize());
    }
}