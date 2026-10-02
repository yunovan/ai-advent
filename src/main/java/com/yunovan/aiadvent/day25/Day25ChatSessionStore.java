package com.yunovan.aiadvent.day25;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Day25ChatSessionStore {

    public record Session(String sessionId, List<Day25Message> messages, Day25TaskMemory memory) {

        public Session {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    private record Data(List<Session> sessions) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private final Map<String, Session> sessions = new LinkedHashMap<>();
    private final int maxSessions;

    public Day25ChatSessionStore(Path storeDir, int maxSessions) {
        this.file = storeDir.resolve("sessions.json");
        this.maxSessions = maxSessions;
        load();
    }

    public synchronized Session get(String sessionId) {
        return sessions.get(sessionId);
    }

    public synchronized List<Session> all() {
        return new ArrayList<>(sessions.values());
    }

    public synchronized int size() {
        return sessions.size();
    }

    public synchronized Session save(Session session) {
        sessions.put(session.sessionId(), session);
        while (sessions.size() > maxSessions) {
            String oldest = sessions.keySet().iterator().next();
            sessions.remove(oldest);
        }
        write();
        return session;
    }

    public synchronized void reset(String sessionId) {
        sessions.remove(sessionId);
        write();
    }

    public synchronized void clear() {
        sessions.clear();
        write();
    }

    private void load() {
        if (Files.notExists(file)) {
            return;
        }
        try {
            Data data = mapper.readValue(file.toFile(), Data.class);
            if (data != null && data.sessions() != null) {
                for (Session session : data.sessions()) {
                    sessions.put(session.sessionId(), session);
                }
            }
        } catch (IOException ex) {
            sessions.clear();
        }
    }

    private void write() {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), new Data(new ArrayList<>(sessions.values())));
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сохранить историю чата: " + ex.getMessage(), ex);
        }
    }
}
