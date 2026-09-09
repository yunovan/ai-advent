package com.yunovan.aiadvent.day07;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.agent.dialog.DialogStore;
import com.yunovan.aiadvent.agent.dialog.DialogSummarizer;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Агент дня 7: диалоги хранятся на диске. Каждый завершённый диалог получает краткий итог,
 * который попадает в долговременную память следующих диалогов.
 */
@Service
public class Day07DialogService {

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final DialogStore store;
    private final DialogContext dialogContext;
    private final DialogSummarizer summarizer;

    public Day07DialogService(
            LlmClient llmClient,
            LlmProperties properties,
            @Qualifier("day7DialogStore") DialogStore store,
            DialogContext dialogContext,
            DialogSummarizer summarizer) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.summarizer = summarizer;
    }

    public Day07StartResponse start() {
        Dialog dialog = store.create();
        return new Day07StartResponse(dialog.id(), dialog.createdAt(), List.of(), memory());
    }

    public Day07ChatResponse chat(String dialogId, String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        String request = userRequest.trim();
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        if (dialog.isFinished()) {
            throw new IllegalArgumentException("Диалог завершён. Начните новый диалог.");
        }

        List<Dialog> previous = previousDialogs(dialogId);
        String systemPrompt = dialogContext.systemPrompt(previous);

        List<ConversationMessage> llmMessages = new ArrayList<>();
        llmMessages.add(ConversationMessage.system(systemPrompt));
        llmMessages.addAll(dialog.messages());
        llmMessages.add(ConversationMessage.user(request));

        LlmReply reply = llmClient.complete(
                CompletionCommand.unconstrained(request), toChatMessages(llmMessages));

        List<ConversationMessage> updated = new ArrayList<>(dialog.messages());
        updated.add(ConversationMessage.user(request));
        updated.add(ConversationMessage.assistant(reply.content()));
        store.save(dialog.withMessages(updated));

        return new Day07ChatResponse(
                dialog.id(),
                request,
                reply.content(),
                properties.model(),
                updated.size(),
                reply.elapsedMs(),
                List.copyOf(updated),
                memory());
    }

    public Day07FinishResponse finish(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        if (!dialog.isFinished()) {
            String summary = summarizer.summarize(dialog);
            dialog = dialog.finished(summary, Instant.now());
            store.save(dialog);
        }
        return new Day07FinishResponse(
                dialog.id(), dialog.finishedAt(), dialog.summary(), dialog.messages().size());
    }

    public Day07DialogInfo get(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        return new Day07DialogInfo(
                dialog.id(),
                dialog.createdAt(),
                dialog.finishedAt(),
                dialog.summary(),
                dialog.messages().size(),
                List.copyOf(dialog.messages()));
    }

    public List<Day07DialogSummary> dialogs() {
        return store.finishedDialogs().stream()
                .map(dialog -> new Day07DialogSummary(
                        dialog.id(),
                        dialog.createdAt(),
                        dialog.finishedAt(),
                        dialog.summary(),
                        dialog.messages().size()))
                .toList();
    }

    private List<Dialog> previousDialogs(String dialogId) {
        return store.finishedDialogs().stream()
                .filter(dialog -> !dialog.id().equals(dialogId))
                .toList();
    }

    private List<DialogMemory> memory() {
        return store.finishedDialogs().stream()
                .map(dialog -> new DialogMemory(dialog.id(), dialog.summary()))
                .toList();
    }

    private static List<ChatCompletionRequest.Message> toChatMessages(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> new ChatCompletionRequest.Message(message.role(), message.content()))
                .toList();
    }
}