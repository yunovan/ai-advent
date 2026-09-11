package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.dialog.DialogStoreException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

public class Day10FileDialogStore {

    private final JsonMapper objectMapper;
    private final Path dataDir;

    public Day10FileDialogStore(Path dataDir) {
        this.objectMapper = JsonMapper.builder().build();
        this.dataDir = dataDir;
    }

    public synchronized Day10Dialog create(Day10Strategy strategy, int windowSize) {
        Day10Dialog dialog = Day10Dialog.create(strategy, windowSize);
        save(dialog);
        return dialog;
    }

    public synchronized Day10Dialog load(String id) {
        Path file = fileFor(id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), Day10Dialog.class);
        } catch (Exception ex) {
            throw new DialogStoreException("Failed to load dialog '" + id + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized void save(Day10Dialog dialog) {
        try {
            Files.createDirectories(dataDir);
            objectMapper.writeValue(fileFor(dialog.id()).toFile(), dialog);
        } catch (Exception ex) {
            throw new DialogStoreException("Failed to save dialog '" + dialog.id() + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized List<Day10Dialog> finishedDialogs() {
        return allDialogs().stream().filter(Day10Dialog::isFinished).toList();
    }

    public synchronized List<Day10Dialog> allDialogs() {
        if (!Files.isDirectory(dataDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dataDir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readQuiet)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Day10Dialog::createdAt).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private Day10Dialog readQuiet(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Day10Dialog.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path fileFor(String id) {
        return dataDir.resolve(id + ".json");
    }
}