package com.yunovan.aiadvent.day21;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day21IndexStore {

    private static final Logger log = LoggerFactory.getLogger(Day21IndexStore.class);

    private final Day21Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public Day21IndexStore(Day21Properties properties) {
        this.properties = properties;
    }

    public void save(Day21IndexFile file) {
        Path target = path(file.strategy());
        try {
            Files.createDirectories(target.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), file);
        } catch (IOException ex) {
            throw new Day21IndexException("Не удалось сохранить индекс " + file.strategy() + ": " + ex.getMessage(), ex);
        }
    }

    public Day21IndexFile load(String strategy) {
        Path source = path(strategy);
        if (!Files.exists(source)) {
            return null;
        }
        try {
            return mapper.readValue(source.toFile(), Day21IndexFile.class);
        } catch (IOException ex) {
            log.warn("День 21: не удалось прочитать индекс {}: {}", strategy, ex.getMessage());
            return null;
        }
    }

    public Path path(String strategy) {
        return Path.of(properties.storeDir(), "index-" + strategy + ".json");
    }
}