package com.yunovan.aiadvent.agent.store;

import com.yunovan.aiadvent.agent.Conversation;
import com.yunovan.aiadvent.day07.Day7Properties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class FileConversationStore implements ConversationStore {

    private final ObjectMapper objectMapper;
    private final Path dataDir;

    public FileConversationStore(ObjectMapper objectMapper, Day7Properties properties) {
        this.objectMapper = objectMapper;
        this.dataDir = Path.of(properties.dataDir());
    }

    @Override
    public synchronized Conversation load(String sessionId) {
        Path file = fileFor(sessionId);
        if (!Files.exists(file)) {
            return Conversation.empty(sessionId);
        }
        try {
            return objectMapper.readValue(file.toFile(), Conversation.class);
        } catch (Exception ex) {
            throw new ConversationStoreException(
                    "Failed to load conversation '" + sessionId + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized void save(Conversation conversation) {
        try {
            Files.createDirectories(dataDir);
            Path file = fileFor(conversation.sessionId());
            objectMapper.writeValue(file.toFile(), conversation);
        } catch (Exception ex) {
            throw new ConversationStoreException(
                    "Failed to save conversation '" + conversation.sessionId() + "': " + ex.getMessage(), ex);
        }
    }

    @Override
    public synchronized void delete(String sessionId) {
        try {
            Files.deleteIfExists(fileFor(sessionId));
        } catch (IOException ex) {
            throw new ConversationStoreException(
                    "Failed to delete conversation '" + sessionId + "': " + ex.getMessage(), ex);
        }
    }

    private Path fileFor(String sessionId) {
        return dataDir.resolve(sanitize(sessionId) + ".json");
    }

    private static String sanitize(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "default";
        }
        String cleaned = sessionId.trim().replaceAll("[^A-Za-z0-9_-]", "-");
        return cleaned.isEmpty() ? "default" : cleaned;
    }
}