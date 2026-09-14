package com.yunovan.aiadvent.day11;

import com.yunovan.aiadvent.agent.dialog.DialogStoreException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

public class Day11FileMemoryStore {

    private static final Pattern UNSAFE = Pattern.compile("[^\\p{L}\\p{N}]");

    private final JsonMapper objectMapper;
    private final Path root;

    public Day11FileMemoryStore(Path root) {
        this.objectMapper = JsonMapper.builder().build();
        this.root = root;
    }

    public synchronized void save(Day11MemoryEntry entry) {
        try {
            Path dir = root.resolve(entry.layer().key());
            Files.createDirectories(dir);
            objectMapper.writeValue(fileFor(entry.layer(), entry.key()).toFile(), entry);
        } catch (Exception ex) {
            throw new DialogStoreException(
                    "Failed to save memory entry '" + entry.key() + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized Day11MemoryEntry find(Day11MemoryLayer layer, String key) {
        Path file = fileFor(layer, key);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), Day11MemoryEntry.class);
        } catch (Exception ex) {
            return null;
        }
    }

    public synchronized boolean delete(Day11MemoryLayer layer, String key) {
        Path file = fileFor(layer, key);
        try {
            return Files.deleteIfExists(file);
        } catch (IOException ex) {
            throw new DialogStoreException(
                    "Failed to delete memory entry '" + key + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized List<Day11MemoryEntry> all(Day11MemoryLayer layer) {
        Path dir = root.resolve(layer.key());
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readQuiet)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Day11MemoryEntry::createdAt))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public synchronized List<Day11MemoryEntry> all() {
        List<Day11MemoryEntry> result = new ArrayList<>();
        for (Day11MemoryLayer layer : Day11MemoryLayer.values()) {
            result.addAll(all(layer));
        }
        return result;
    }

    private Day11MemoryEntry readQuiet(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Day11MemoryEntry.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path fileFor(Day11MemoryLayer layer, String key) {
        return root.resolve(layer.key()).resolve(sanitize(key) + ".json");
    }

    static String sanitize(String key) {
        if (key == null || key.isBlank()) {
            return "entry";
        }
        String safe = UNSAFE.matcher(key.toLowerCase().trim()).replaceAll("_");
        safe = safe.replaceAll("_+$", "");
        if (safe.isEmpty()) {
            safe = "entry";
        }
        if (safe.length() > 64) {
            safe = safe.substring(0, 64);
        }
        return safe;
    }
}
