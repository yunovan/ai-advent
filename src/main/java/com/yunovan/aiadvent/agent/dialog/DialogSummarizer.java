package com.yunovan.aiadvent.agent.dialog;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DialogSummarizer {

    private static final String SUMMARIZE_SYSTEM_PROMPT =
            "Ты подводишь итог диалога для долговременной памяти агента. "
                    + "Напиши 2-4 предложения: о чём был диалог и какие важные факты стоит запомнить.";

    private static final int MAX_TRANSCRIPT_MESSAGES = 40;

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public DialogSummarizer(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public String summarize(Dialog dialog) {
        if (dialog == null || dialog.messages().isEmpty()) {
            return "Пустой диалог без сообщений.";
        }
        if (properties.hasApiKey()) {
            try {
                return llmClient.complete(CompletionCommand
                                .unconstrained(transcript(dialog.messages()))
                                .withSystemPrompt(SUMMARIZE_SYSTEM_PROMPT))
                        .content()
                        .trim();
            } catch (LlmException ex) {
                return localSummary(dialog);
            }
        }
        return localSummary(dialog);
    }

    private static String transcript(List<ConversationMessage> messages) {
        int from = Math.max(0, messages.size() - MAX_TRANSCRIPT_MESSAGES);
        List<ConversationMessage> visible = messages.subList(from, messages.size());
        StringBuilder builder = new StringBuilder();
        for (ConversationMessage message : visible) {
            builder.append(message.role()).append(": ").append(message.content()).append("\n");
        }
        if (from > 0) {
            builder.insert(0, "...и ещё " + from + " сообщений ранее\n");
        }
        return builder.toString();
    }

    private static String localSummary(Dialog dialog) {
        String firstUser = dialog.messages().stream()
                .filter(message -> "user".equals(message.role()))
                .map(ConversationMessage::content)
                .findFirst()
                .orElse(null);
        StringBuilder builder = new StringBuilder("Диалог из ")
                .append(dialog.messages().size())
                .append(" сообщений");
        if (firstUser != null) {
            String excerpt = firstUser.length() > 200 ? firstUser.substring(0, 200) + "…" : firstUser;
            builder.append("; первый вопрос: «").append(excerpt).append("»");
        }
        return builder.toString();
    }
}