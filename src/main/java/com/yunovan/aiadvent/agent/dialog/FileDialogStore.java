package com.yunovan.aiadvent.agent.dialog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

public class FileDialogStore implements DialogStore {

    private final JsonMapper objectMapper;
    private final Path dataDir;

    public FileDialogStore(Path dataDir) {
        this.objectMapper = JsonMapper.builder().build();
        this.dataDir = dataDir;
    }

    @Override
    public synchronized Dialog create() {
        Dialog dialog = Dialog.create();
        save(dialog);
        return dialog;
    }

    @Override
    public synchronized Dialog load(String id) {
        Path file = fileFor(id);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), Dialog.class);
        } catch (Exception ex) {
            throw new DialogStoreException("Failed to load dialog '" + id + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized void save(Dialog dialog) {
        try {
            Files.createDirectories(dataDir);
            objectMapper.writeValue(fileFor(dialog.id()).toFile(), dialog);
        } catch (Exception ex) {
            throw new DialogStoreException("Failed to save dialog '" + dialog.id() + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized List<Dialog> finishedDialogs() {
        if (!Files.isDirectory(dataDir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dataDir)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readQuiet)
                    .filter(Objects::nonNull)
                    .filter(Dialog::isFinished)
                    .sorted(Comparator.comparing(Dialog::finishedAt).reversed())
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    private Dialog readQuiet(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Dialog.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private Path fileFor(String id) {
        return dataDir.resolve(id + ".json");
    }
}