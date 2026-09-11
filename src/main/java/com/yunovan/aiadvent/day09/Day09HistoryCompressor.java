package com.yunovan.aiadvent.day09;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Day09HistoryCompressor {

    private static final String SYSTEM_PROMPT =
            "Ты сжимаешь историю диалога для экономии токенов. "
                    + "Напиши 2–4 предложения: о чём говорили в этой части и какие факты стоит запомнить.";

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public Day09HistoryCompressor(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public String summarizeChunk(List<ConversationMessage> chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        if (properties.hasApiKey()) {
            try {
                return llmClient.complete(CompletionCommand
                                .unconstrained(transcript(chunk))
                                .withSystemPrompt(SYSTEM_PROMPT))
                        .content()
                        .trim();
            } catch (LlmException ex) {
                return localSummary(chunk);
            }
        }
        return localSummary(chunk);
    }

    private static String transcript(List<ConversationMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ConversationMessage message : messages) {
            builder.append(message.role()).append(": ").append(message.content()).append("\n");
        }
        return builder.toString();
    }

    private static String localSummary(List<ConversationMessage> chunk) {
        String firstUser = chunk.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ConversationMessage::content)
                .findFirst()
                .orElse(null);
        StringBuilder builder = new StringBuilder("Фрагмент из ")
                .append(chunk.size())
                .append(" сообщений");
        if (firstUser != null) {
            String excerpt = firstUser.length() > 200 ? firstUser.substring(0, 200) + "…" : firstUser;
            builder.append("; начало: «").append(excerpt).append("»");
        }
        return builder.toString();
    }
}