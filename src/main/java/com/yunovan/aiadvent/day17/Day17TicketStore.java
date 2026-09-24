package com.yunovan.aiadvent.day17;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Day17TicketStore {

    private record Data(List<Day17Ticket> tasks, Map<String, List<Day17Comment>> comments) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;

    public Day17TicketStore(Path storeDir) {
        this.file = storeDir.resolve("tracker.json");
    }

    public synchronized void save(Day17Ticket ticket) {
        Data data = load();
        data.tasks().removeIf(t -> t.id().equals(ticket.id()));
        data.tasks().add(ticket);
        data.tasks().sort((a, b) -> b.createdAt().compareTo(a.createdAt()));
        write(data);
    }

    public synchronized Day17Ticket findById(String id) {
        return load().tasks().stream().filter(t -> t.id().equals(id)).findFirst().orElse(null);
    }

    public synchronized List<Day17Ticket> all() {
        return load().tasks();
    }

    public synchronized void saveComment(Day17Comment comment) {
        Data data = load();
        data.comments().computeIfAbsent(comment.taskId(), k -> new ArrayList<>()).add(comment);
        write(data);
    }

    public synchronized List<Day17Comment> comments(String taskId) {
        return List.copyOf(load().comments().getOrDefault(taskId, List.of()));
    }

    private Data load() {
        if (Files.notExists(file)) {
            return new Data(new ArrayList<>(), new HashMap<>());
        }
        try {
            return mapper.readValue(file.toFile(), Data.class);
        } catch (IOException ex) {
            return new Data(new ArrayList<>(), new HashMap<>());
        }
    }

    private void write(Data data) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), data);
        } catch (IOException ex) {
            throw new RuntimeException("Не удалось сохранить данные трекера: " + ex.getMessage(), ex);
        }
    }
}