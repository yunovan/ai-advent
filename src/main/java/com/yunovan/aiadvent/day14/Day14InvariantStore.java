package com.yunovan.aiadvent.day14;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

public final class Day14InvariantStore {

    private final JsonMapper objectMapper;
    private final Path dataDir;

    public Day14InvariantStore(Path dataDir) {
        this.objectMapper = JsonMapper.builder().build();
        this.dataDir = dataDir;
    }

    public synchronized Day14Invariant save(Day14Invariant invariant) {
        try {
            Files.createDirectories(dataDir);
            objectMapper.writeValue(fileFor(invariant.id()).toFile(), invariant);
            return invariant;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to save invariant '" + invariant.id() + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized Day14Invariant load(String id) {
        Path file = fileFor(id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), Day14Invariant.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load invariant '" + id + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized List<Day14Invariant> all() {
        if (!Files.isDirectory(dataDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dataDir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readQuiet)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Day14Invariant::createdAt).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public synchronized boolean delete(String id) {
        try {
            return Files.deleteIfExists(fileFor(id));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to delete invariant '" + id + "': " + ex.getMessage(), ex);
        }
    }

    private Day14Invariant readQuiet(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Day14Invariant.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path fileFor(String id) {
        return dataDir.resolve(id + ".json");
    }
}