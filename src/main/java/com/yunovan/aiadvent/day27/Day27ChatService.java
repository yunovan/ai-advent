package com.yunovan.aiadvent.day27;

import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26InstalledModel;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class Day27ChatService {

    private final Day26LocalLlmClient client;
    private final Day26Properties llmProperties;
    private final Day27Properties properties;
    private final Day27ChatSessionStore store;

    public Day27ChatService(Day26LocalLlmClient client, Day26Properties llmProperties,
                            Day27Properties properties, Day27ChatSessionStore store) {
        this.client = client;
        this.llmProperties = llmProperties;
        this.properties = properties;
        this.store = store;
    }

    public Day27HealthResponse health() {
        String endpoint = llmProperties.endpoint();
        String model = llmProperties.model();
        try {
            String version = client.version();
            List<Day26InstalledModel> models = client.models();
            boolean installed = models.stream().anyMatch(m -> matches(m.name(), model));
            return new Day27HealthResponse(endpoint, model, true, version, installed, false,
                    properties.historyLimit(), properties.maxSessions(), store.size(), "");
        } catch (Day26LlmException ex) {
            return new Day27HealthResponse(endpoint, model, false, "", false, false,
                    properties.historyLimit(), properties.maxSessions(), store.size(), ex.getMessage());
        } catch (RuntimeException ex) {
            return new Day27HealthResponse(endpoint, model, false, "", false, false,
                    properties.historyLimit(), properties.maxSessions(), store.size(),
                    "Не удалось проверить локальный LLM: " + message(ex));
        }
    }

    public List<Day27SessionView> sessions() {
        List<Day27SessionView> views = new ArrayList<>();
        for (Day27ChatSessionStore.Session session : store.all()) {
            views.add(view(session));
        }
        return views;
    }

    public List<Day27Message> history(String sessionId) {
        Day27ChatSessionStore.Session session = store.get(normalize(sessionId));
        return session == null ? List.of() : session.messages();
    }

    public void reset(String sessionId) {
        store.reset(normalize(sessionId));
    }

    public Day27ChatTurn chat(String rawSessionId, String rawMessage) {
        String sessionId = normalize(rawSessionId);
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }
        Day27ChatSessionStore.Session session = store.get(sessionId);
        List<Day27Message> stored = session == null ? List.of() : session.messages();
        int turn = stored.size() / 2 + 1;

        Day26Answer answer = client.chat(request(stored, message));

        List<Day27Message> messages = new ArrayList<>(stored);
        messages.add(new Day27Message("user", message, turn, 0, 0, 0, 0.0));
        messages.add(new Day27Message("assistant", answer.reply(), turn, answer.latencyMs(),
                answer.promptTokens(), answer.outputTokens(), answer.tokensPerSecond()));
        messages = trim(messages);
        store.save(new Day27ChatSessionStore.Session(sessionId, messages));

        return new Day27ChatTurn(sessionId, turn, message, answer.reply(), answer.model(),
                answer.endpoint(), answer.latencyMs(), answer.promptTokens(), answer.outputTokens(),
                answer.tokensPerSecond(), messages, messages.size());
    }

    private List<Day26ChatMessage> request(List<Day27Message> stored, String message) {
        List<Day26ChatMessage> payload = new ArrayList<>();
        payload.add(new Day26ChatMessage("system", properties.systemPrompt()));
        for (Day27Message item : stored) {
            payload.add(new Day26ChatMessage(item.role(), item.text()));
        }
        payload.add(new Day26ChatMessage("user", message));
        return payload;
    }

    private List<Day27Message> trim(List<Day27Message> messages) {
        if (messages.size() <= properties.historyLimit()) {
            return messages;
        }
        int from = messages.size() - properties.historyLimit();
        if (from % 2 != 0) {
            from++;
        }
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    private static Day27SessionView view(Day27ChatSessionStore.Session session) {
        List<Day27Message> messages = session.messages();
        String last = messages.isEmpty() ? null : messages.getLast().text();
        return new Day27SessionView(session.sessionId(), messages.size() / 2, messages.size(), last);
    }

    private static boolean matches(String installed, String expected) {
        if (installed == null || expected == null) {
            return false;
        }
        return installed.trim().toLowerCase(Locale.ROOT)
                .equals(expected.trim().toLowerCase(Locale.ROOT));
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String normalize(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
    }
}
