package com.yunovan.aiadvent.day17;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class Day17TrackerService implements Day17TrackerApi {

    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ISO_INSTANT;

    private final Day17TicketStore store;

    public Day17TrackerService(Day17TicketStore store) {
        this.store = store;
    }

    @Override
    public Day17Ticket createTask(String title, String description, String assignee) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Укажите заголовок задачи (title)");
        }
        Day17Ticket ticket = new Day17Ticket(
                "t-" + shortId(),
                title.trim(),
                blankToNull(description),
                blankToNull(assignee),
                "new",
                CREATED_AT.format(Instant.now()));
        store.save(ticket);
        return ticket;
    }

    @Override
    public List<Day17Ticket> listTasks(String status) {
        List<Day17Ticket> tasks = store.all();
        if (status == null || status.isBlank() || status.equalsIgnoreCase("all")) {
            return tasks;
        }
        return tasks.stream()
                .filter(t -> t.status().equalsIgnoreCase(status.trim()))
                .toList();
    }

    @Override
    public Day17Comment addComment(String taskId, String text) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Укажите идентификатор задачи (taskId)");
        }
        Day17Ticket ticket = store.findById(taskId.trim());
        if (ticket == null) {
            throw new IllegalArgumentException("Задача не найдена: " + taskId);
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Укажите текст комментария (text)");
        }
        Day17Comment comment = new Day17Comment(
                "c-" + shortId(),
                ticket.id(),
                "ai-advent",
                text.trim(),
                CREATED_AT.format(Instant.now()));
        store.saveComment(comment);
        return comment;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }
}