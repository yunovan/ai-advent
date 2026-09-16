package com.yunovan.aiadvent.day13;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

public final class Day13TaskStore {

    private final JsonMapper objectMapper;
    private final Path dataDir;

    public Day13TaskStore(Path dataDir) {
        this.objectMapper = JsonMapper.builder().build();
        this.dataDir = dataDir;
    }

    public synchronized Day13Task save(Day13Task task) {
        try {
            Files.createDirectories(dataDir);
            objectMapper.writeValue(fileFor(task.id()).toFile(), task);
            return task;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to save task '" + task.id() + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized Day13Task load(String id) {
        Path file = fileFor(id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), Day13Task.class);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to load task '" + id + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized List<Day13Task> all() {
        if (!Files.isDirectory(dataDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dataDir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readQuiet)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Day13Task::createdAt).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public synchronized boolean delete(String id) {
        try {
            return Files.deleteIfExists(fileFor(id));
        } catch (IOException ex) {
            throw new RuntimeException("Failed to delete task '" + id + "': " + ex.getMessage(), ex);
        }
    }

    private Day13Task readQuiet(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Day13Task.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path fileFor(String id) {
        return dataDir.resolve(id + ".json");
    }
}