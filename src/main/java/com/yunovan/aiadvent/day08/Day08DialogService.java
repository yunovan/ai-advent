package com.yunovan.aiadvent.day08;

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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Агент дня 8: диалоги с памятью о прошлых диалогах, как у дня 7, плюс подсчёт токенов
 * (запрос, история текущего диалога, память-контекст и ответ) и стоимости. Страхует диалог
 * от выхода за контекстное окно модели.
 */
@Service
public class Day08DialogService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day8Properties day8Properties;
    private final DialogStore store;
    private final DialogContext dialogContext;
    private final DialogSummarizer summarizer;
    private final TokenEstimator estimator;

    public Day08DialogService(
            LlmClient llmClient,
            LlmProperties properties,
            Day8Properties day8Properties,
            @Qualifier("day8DialogStore") DialogStore store,
            DialogContext dialogContext,
            DialogSummarizer summarizer,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day8Properties = day8Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.summarizer = summarizer;
        this.estimator = estimator;
    }

    public Day08StartResponse start() {
        Dialog dialog = store.create();
        return new Day08StartResponse(dialog.id(), dialog.createdAt(), List.of(), memory());
    }

    public Day08ChatResponse chat(String dialogId, String userRequest, Long contextLimitOverride) {
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
        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day8Properties.contextLimit();

        String systemPrompt = dialogContext.systemPrompt(previousDialogs(dialogId));
        long contextTokens = estimator.estimate(systemPrompt);
        List<ConversationMessage> history = dialog.messages();
        long historyTokens = estimator.estimate(history);
        long requestTokens = estimator.estimate(request);

        List<ConversationMessage> llmMessages = new ArrayList<>();
        llmMessages.add(ConversationMessage.system(systemPrompt));
        llmMessages.addAll(history);
        llmMessages.add(ConversationMessage.user(request));
        long promptTokens = estimator.estimate(llmMessages);

        if (promptTokens > limit) {
            return Day08ChatResponse.exceeded(
                    dialog.id(),
                    request,
                    overflowMessage(promptTokens, limit),
                    contextTokens,
                    requestTokens,
                    historyTokens,
                    promptTokens,
                    limit,
                    history,
                    memory());
        }

        LlmReply reply = llmClient.complete(
                CompletionCommand.unconstrained(request), toChatMessages(llmMessages));
        long responseTokens = estimator.estimate(reply.content());

        List<ConversationMessage> updated = new ArrayList<>(history);
        updated.add(ConversationMessage.user(request));
        updated.add(ConversationMessage.assistant(reply.content()));
        store.save(dialog.withMessages(updated));

        return new Day08ChatResponse(
                dialog.id(),
                request,
                reply.content(),
                properties.model(),
                updated.size(),
                reply.elapsedMs(),
                contextTokens,
                requestTokens,
                historyTokens,
                promptTokens,
                responseTokens,
                reply.promptTokens(),
                reply.completionTokens(),
                reply.totalTokens(),
                reply.costUsd(),
                estimatedTurnCost(promptTokens, responseTokens),
                estimatedCumulativeCost(updated, contextTokens),
                limit,
                false,
                List.copyOf(updated),
                memory());
    }

    public Day08FinishResponse finish(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        if (!dialog.isFinished()) {
            String summary = summarizer.summarize(dialog);
            dialog = dialog.finished(summary, Instant.now());
            store.save(dialog);
        }
        return new Day08FinishResponse(
                dialog.id(), dialog.finishedAt(), dialog.summary(), dialog.messages().size());
    }

    public Day08DialogInfo get(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        return new Day08DialogInfo(
                dialog.id(),
                dialog.createdAt(),
                dialog.finishedAt(),
                dialog.summary(),
                dialog.messages().size(),
                List.copyOf(dialog.messages()));
    }

    public List<Day08DialogSummary> dialogs() {
        return store.finishedDialogs().stream()
                .map(dialog -> new Day08DialogSummary(
                        dialog.id(),
                        dialog.createdAt(),
                        dialog.finishedAt(),
                        dialog.summary(),
                        dialog.messages().size()))
                .toList();
    }

    public Day08GrowthReport metrics(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        List<Dialog> finished = store.finishedDialogs();
        List<Dialog> previous = finished.stream()
                .filter(d -> !d.id().equals(dialogId))
                .toList();
        long contextTokens = estimator.estimate(dialogContext.systemPrompt(previous));
        List<Day08GrowthTurn> turns = new ArrayList<>();
        long running = contextTokens;
        BigDecimal cumulative = BigDecimal.ZERO;
        int turnCount = 0;

        for (int i = 0; i + 1 < dialog.messages().size(); i += 2) {
            ConversationMessage user = dialog.messages().get(i);
            ConversationMessage assistant = dialog.messages().get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            turnCount++;
            long userTokens = estimator.estimate(user.content());
            long responseTokens = estimator.estimate(assistant.content());
            long promptTokens = running + userTokens;
            BigDecimal turnCost = estimatedTurnCost(promptTokens, responseTokens);
            cumulative = cumulative.add(turnCost);
            running += userTokens + responseTokens;
            turns.add(new Day08GrowthTurn(
                    turnCount, promptTokens, responseTokens, running, turnCost, cumulative));
        }

        List<Day08DialogComparison> comparisons = previous.stream()
                .filter(d -> d.finishedAt() != null)
                .sorted(Comparator.comparing(Dialog::finishedAt))
                .map(d -> comparisonFor(d, finished))
                .toList();

        return new Day08GrowthReport(
                dialog.id(),
                day8Properties.contextLimit(),
                day8Properties.inputPrice(),
                day8Properties.outputPrice(),
                running,
                cumulative,
                List.copyOf(turns),
                List.copyOf(comparisons));
    }

    private Day08DialogComparison comparisonFor(Dialog previous, List<Dialog> finished) {
        List<Dialog> memoryBefore = finished.stream()
                .filter(d -> !d.id().equals(previous.id()))
                .filter(d -> d.finishedAt() != null
                        && previous.finishedAt() != null
                        && d.finishedAt().isBefore(previous.finishedAt()))
                .toList();
        long running = estimator.estimate(dialogContext.systemPrompt(memoryBefore));
        BigDecimal cumulative = BigDecimal.ZERO;
        int turnCount = 0;
        for (int i = 0; i + 1 < previous.messages().size(); i += 2) {
            ConversationMessage user = previous.messages().get(i);
            ConversationMessage assistant = previous.messages().get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            turnCount++;
            long userTokens = estimator.estimate(user.content());
            long responseTokens = estimator.estimate(assistant.content());
            cumulative = cumulative.add(estimatedTurnCost(running + userTokens, responseTokens));
            running += userTokens + responseTokens;
        }
        return new Day08DialogComparison(
                previous.id(),
                previous.finishedAt(),
                previous.summary(),
                previous.messages().size(),
                turnCount,
                running,
                cumulative);
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

    private BigDecimal estimatedCumulativeCost(List<ConversationMessage> messages, long contextTokens) {
        long running = contextTokens;
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int i = 0; i + 1 < messages.size(); i += 2) {
            ConversationMessage user = messages.get(i);
            ConversationMessage assistant = messages.get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            long userTokens = estimator.estimate(user.content());
            long responseTokens = estimator.estimate(assistant.content());
            cumulative = cumulative.add(estimatedTurnCost(running + userTokens, responseTokens));
            running += userTokens + responseTokens;
        }
        return cumulative;
    }

    private BigDecimal estimatedTurnCost(long promptTokens, long responseTokens) {
        BigDecimal prompt = BigDecimal.valueOf(promptTokens).divide(MILLION);
        BigDecimal response = BigDecimal.valueOf(responseTokens).divide(MILLION);
        return prompt.multiply(day8Properties.inputPrice())
                .add(response.multiply(day8Properties.outputPrice()));
    }

    private static List<ChatCompletionRequest.Message> toChatMessages(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> new ChatCompletionRequest.Message(message.role(), message.content()))
                .toList();
    }

    private static String overflowMessage(long promptTokens, long limit) {
        return "Диалог превысил контекстное окно модели: " + promptTokens + " токенов при лимите " + limit
                + ". Вопрос не отправлен в модель — она бы вернула ошибку переполнения "
                + "(context_length_exceeded). Завершите диалог и начните новый с меньшим накопленным контекстом.";
    }
}