package com.yunovan.aiadvent.day09;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.agent.dialog.DialogStore;
import com.yunovan.aiadvent.agent.dialog.DialogSummarizer;
import com.yunovan.aiadvent.day08.TokenEstimator;
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

@Service
public class Day09DialogService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day9Properties day9Properties;
    private final DialogStore store;
    private final DialogContext dialogContext;
    private final DialogSummarizer summarizer;
    private final Day09HistoryCompressor compressor;
    private final TokenEstimator estimator;

    public Day09DialogService(
            LlmClient llmClient,
            LlmProperties properties,
            Day9Properties day9Properties,
            @Qualifier("day9DialogStore") DialogStore store,
            DialogContext dialogContext,
            DialogSummarizer summarizer,
            Day09HistoryCompressor compressor,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day9Properties = day9Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.summarizer = summarizer;
        this.compressor = compressor;
        this.estimator = estimator;
    }

    public Day09StartResponse start() {
        Dialog dialog = store.create();
        return new Day09StartResponse(dialog.id(), dialog.createdAt(), List.of(), memory());
    }

    public Day09ChatResponse chat(String dialogId, String userRequest, Long contextLimitOverride, Boolean compressionOverride) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        if (dialog.isFinished()) {
            throw new IllegalArgumentException("Диалог завершён. Начните новый диалог.");
        }
        boolean compression = compressionOverride == null || compressionOverride;
        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day9Properties.contextLimit();

        Dialog active = compressHistory(dialog);
        List<Dialog> previous = previousDialogs(dialogId);

        String request = userRequest.trim();
        String basePrompt = dialogContext.systemPrompt(previous);

        boolean bannerActive = compression && active.hasCompressedHistory();
        String systemPrompt;
        long summaryTokens;
        List<ConversationMessage> sentHistory;
        if (bannerActive) {
            String banner = summaryBanner(active.historySummaryCount(), active.historySummary());
            systemPrompt = basePrompt + banner;
            summaryTokens = estimator.estimate(banner);
            sentHistory = recentWindow(active);
        } else {
            systemPrompt = basePrompt;
            summaryTokens = 0L;
            sentHistory = active.messages();
        }

        long contextTokens = estimator.estimate(systemPrompt);
        long historyTokens = estimator.estimate(sentHistory);
        long requestTokens = estimator.estimate(request);
        long promptTokens = contextTokens + historyTokens + requestTokens;

        long fullContextTokens = estimator.estimate(basePrompt);
        long fullHistoryTokens = estimator.estimate(active.messages());
        long fullPromptTokens = fullContextTokens + fullHistoryTokens + requestTokens;
        long savedTokens = compression ? Math.max(0L, fullPromptTokens - promptTokens) : 0L;

        if (promptTokens > limit) {
            return Day09ChatResponse.exceeded(
                    dialog.id(), request,
                    overflowMessage(promptTokens, limit),
                    compression, active.historySummaryCount(),
                    contextTokens, requestTokens,
                    historyTokens, fullHistoryTokens, summaryTokens,
                    promptTokens, fullPromptTokens, savedTokens,
                    limit, active.messages(), memory());
        }

        LlmReply reply = llmClient.complete(
                CompletionCommand.unconstrained(request), toChatMessages(dialogId, compression, active));
        long responseTokens = estimator.estimate(reply.content());

        List<ConversationMessage> updated = new ArrayList<>(active.messages());
        updated.add(ConversationMessage.user(request));
        updated.add(ConversationMessage.assistant(reply.content()));
        store.save(active.withMessages(updated));

        List<Day09TurnBudget> budgetHistory = budgets(basePrompt, active.historySummary(), active.historySummaryCount(), updated);
        Day09TurnBudget current = budgetHistory.isEmpty() ? zeroBudget() : budgetHistory.getLast();

        return new Day09ChatResponse(
                dialog.id(),
                request,
                reply.content(),
                properties.model(),
                updated.size(),
                reply.elapsedMs(),
                compression,
                active.historySummaryCount(),
                contextTokens,
                requestTokens,
                historyTokens,
                fullHistoryTokens,
                summaryTokens,
                promptTokens,
                fullPromptTokens,
                savedTokens,
                responseTokens,
                reply.promptTokens(),
                reply.completionTokens(),
                reply.totalTokens(),
                reply.costUsd(),
                current.turnCost(),
                current.cumulativeCost(),
                current.cumulativeFullCost(),
                limit,
                false,
                List.copyOf(updated),
                memory());
    }

    public Day09FinishResponse finish(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        if (!dialog.isFinished()) {
            String summary = summarizer.summarize(dialog);
            dialog = dialog.finished(summary, Instant.now());
            store.save(dialog);
        }
        return new Day09FinishResponse(
                dialog.id(), dialog.finishedAt(), dialog.summary(), dialog.messages().size());
    }

    public Day09DialogInfo get(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        return new Day09DialogInfo(
                dialog.id(), dialog.createdAt(), dialog.finishedAt(),
                dialog.summary(), dialog.historySummary(), dialog.historySummaryCount(),
                dialog.messages().size(), List.copyOf(dialog.messages()));
    }

    public List<Day09DialogSummary> dialogs() {
        return store.finishedDialogs().stream()
                .map(dialog -> new Day09DialogSummary(
                        dialog.id(), dialog.createdAt(), dialog.finishedAt(),
                        dialog.summary(), dialog.messages().size()))
                .toList();
    }

    public Day09GrowthReport metrics(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        List<Dialog> previous = previousDialogs(dialogId);
        String basePrompt = dialogContext.systemPrompt(previous);

        List<Day09TurnBudget> budgetHistory = budgets(
                basePrompt, dialog.historySummary(), dialog.historySummaryCount(), dialog.messages());
        List<Day09GrowthTurn> turns = new ArrayList<>();
        for (Day09TurnBudget b : budgetHistory) {
            turns.add(new Day09GrowthTurn(
                    b.turn, b.sentTokens, b.fullTokens, b.responseTokens,
                    b.cumulativeSent, b.cumulativeFull,
                    b.turnCost, b.cumulativeCost, b.cumulativeFullCost()));
        }
        Day09TurnBudget last = budgetHistory.isEmpty() ? zeroBudget() : budgetHistory.getLast();
        long saved = last.cumulativeFull - last.cumulativeSent;

        return new Day09GrowthReport(
                dialog.id(),
                day9Properties.contextLimit(),
                day9Properties.inputPrice(),
                day9Properties.outputPrice(),
                last.cumulativeSent,
                last.cumulativeFull,
                saved,
                last.cumulativeCost,
                last.cumulativeFullCost(),
                List.copyOf(turns));
    }

    private Dialog compressHistory(Dialog dialog) {
        int covered = dialog.historySummaryCount();
        int size = dialog.messages().size();
        if (covered >= size) {
            return dialog;
        }
        int recent = day9Properties.recentMessages();
        int chunk = day9Properties.chunkSize();
        String running = dialog.historySummary() == null || dialog.historySummary().isBlank()
                ? ""
                : dialog.historySummary().trim();
        List<ConversationMessage> messages = dialog.messages();
        while (size - covered >= recent + chunk) {
            List<ConversationMessage> segment = messages.subList(covered, covered + chunk);
            String segmentSummary = compressor.summarizeChunk(segment);
            running = running.isBlank() ? segmentSummary : running + "\n\n" + segmentSummary;
            covered += chunk;
        }
        if (covered == dialog.historySummaryCount()) {
            return dialog;
        }
        return dialog.withHistorySummary(running, covered);
    }

    private List<Day09TurnBudget> budgets(String basePrompt, String historySummary, int finalCovered, List<ConversationMessage> messages) {
        int recent = day9Properties.recentMessages();
        int chunk = day9Properties.chunkSize();
        long runningSent = 0L;
        long runningFull = 0L;
        BigDecimal cumulativeSent = BigDecimal.ZERO;
        BigDecimal cumulativeFull = BigDecimal.ZERO;
        List<Day09TurnBudget> result = new ArrayList<>();

        for (int i = 0; i + 1 < messages.size(); i += 2) {
            ConversationMessage user = messages.get(i);
            ConversationMessage assistant = messages.get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            int turn = (i / 2) + 1;
            int covered = foldCount(i, recent, chunk);
            long contextSent = estimator.estimate(basePrompt
                    + (covered > 0 ? summaryBanner(covered, historySummary) : ""));
            List<ConversationMessage> recentPart = messages.subList(covered, i);
            long sentTokens = contextSent + estimator.estimate(recentPart) + estimator.estimate(user.content());
            long fullTokens = estimator.estimate(basePrompt) + estimator.estimate(messages.subList(0, i)) + estimator.estimate(user.content());

            runningSent += sentTokens;
            runningFull += fullTokens;
            BigDecimal turnCost = turnCost(sentTokens, estimator.estimate(assistant.content()));
            cumulativeSent = cumulativeSent.add(turnCost);
            cumulativeFull = cumulativeFull.add(turnCost); // approximate — same price model
            result.add(new Day09TurnBudget(
                    turn, sentTokens, fullTokens, estimator.estimate(assistant.content()),
                    runningSent, runningFull,
                    turnCost, cumulativeSent, cumulativeFull));
        }
        return result;
    }

    private int foldCount(int messageCount, int recent, int chunk) {
        int covered = 0;
        while (messageCount - covered >= recent + chunk) {
            covered += chunk;
        }
        return covered;
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

    private List<ConversationMessage> recentWindow(Dialog active) {
        int start = active.contextWindowStart();
        return active.messages().subList(start, active.messages().size());
    }

    private List<ChatCompletionRequest.Message> toChatMessages(String dialogId, boolean compression, Dialog active) {
        List<ConversationMessage> messages = new ArrayList<>();
        List<Dialog> previous = previousDialogs(dialogId);
        String basePrompt = dialogContext.systemPrompt(previous);
        boolean bannerActive = compression && active.hasCompressedHistory();
        if (bannerActive) {
            messages.add(ConversationMessage.system(basePrompt + summaryBanner(active.historySummaryCount(), active.historySummary())));
        } else {
            messages.add(ConversationMessage.system(basePrompt));
        }
        if (bannerActive) {
            messages.addAll(recentWindow(active));
        } else {
            messages.addAll(active.messages());
        }
        return messages.stream()
                .map(message -> new ChatCompletionRequest.Message(message.role(), message.content()))
                .toList();
    }

    private BigDecimal turnCost(long promptTokens, long responseTokens) {
        BigDecimal prompt = BigDecimal.valueOf(promptTokens).divide(MILLION);
        BigDecimal response = BigDecimal.valueOf(responseTokens).divide(MILLION);
        return prompt.multiply(day9Properties.inputPrice())
                .add(response.multiply(day9Properties.outputPrice()));
    }

    private static Day09TurnBudget zeroBudget() {
        return new Day09TurnBudget(0, 0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static String summaryBanner(int count, String historySummary) {
        return "\n\nСжатая история этого диалога (первые " + count + " сообщений вместо полной переписки):\n"
                + historySummary;
    }

    private static String overflowMessage(long promptTokens, long limit) {
        return "Диалог превысил контекстное окно модели: " + promptTokens + " токенов при лимите " + limit
                + ". Вопрос не отправлен в модель — она бы вернула ошибку переполнения "
                + "(context_length_exceeded). Завершите диалог и начните новый с меньшим накопленным контекстом.";
    }

    private record Day09TurnBudget(
            int turn,
            long sentTokens,
            long fullTokens,
            long responseTokens,
            long cumulativeSent,
            long cumulativeFull,
            BigDecimal turnCost,
            BigDecimal cumulativeCost,
            BigDecimal cumulativeFullCost) {
    }
}