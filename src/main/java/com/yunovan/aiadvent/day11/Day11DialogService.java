package com.yunovan.aiadvent.day11;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
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
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class Day11DialogService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day11Properties day11Properties;
    private final DialogStore store;
    private final DialogContext dialogContext;
    private final DialogSummarizer summarizer;
    private final Day11FactExtractor factExtractor;
    private final Day11FileMemoryStore memoryStore;
    private final TokenEstimator estimator;

    public Day11DialogService(
            LlmClient llmClient,
            LlmProperties properties,
            Day11Properties day11Properties,
            @Qualifier("day11DialogStore") DialogStore store,
            DialogContext dialogContext,
            DialogSummarizer summarizer,
            Day11FactExtractor factExtractor,
            @Qualifier("day11MemoryStore") Day11FileMemoryStore memoryStore,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day11Properties = day11Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.summarizer = summarizer;
        this.factExtractor = factExtractor;
        this.memoryStore = memoryStore;
        this.estimator = estimator;
    }

    public Day11StartResponse start() {
        Dialog dialog = store.create();
        return new Day11StartResponse(dialog.id(), dialog.createdAt(), Day11MemoryLayer.values());
    }

    public Day11ChatResponse chat(String dialogId, String userRequest, Long contextLimitOverride) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        Dialog dialog = require(dialogId);
        if (dialog.isFinished()) {
            throw new IllegalArgumentException("Диалог завершён. Начните новый диалог.");
        }
        String request = userRequest.trim();

        List<Day11MemoryEntry> candidates = factExtractor.extract(request);
        for (Day11MemoryEntry candidate : candidates) {
            memoryStore.save(candidate);
        }

        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day11Properties.contextLimit();

        String basePrompt = basePrompt();
        int shortTermWindow = day11Properties.shortTermWindow();
        List<ConversationMessage> fullHistory = dialog.messages();
        List<ConversationMessage> sentHistory = lastN(fullHistory, shortTermWindow);

        List<Day11MemoryEntry> workingEntries = memoryStore.all(Day11MemoryLayer.WORKING);
        List<Day11MemoryEntry> longTermEntries = memoryStore.all(Day11MemoryLayer.LONG_TERM);

        String memoryBlock = memoryLayersBlock(shortTermWindow, workingEntries, longTermEntries);
        String systemPrompt = basePrompt + "\n\n" + memoryBlock;

        long contextTokens = estimator.estimate(systemPrompt);
        long requestTokens = estimator.estimate(request);
        long shortTermTokens = estimator.estimate(sentHistory);
        long workingTokens = estimator.estimate(blockText(workingEntries));
        long longTermTokens = estimator.estimate(blockText(longTermEntries));
        long promptTokens = contextTokens + shortTermTokens + requestTokens;

        if (promptTokens > limit) {
            return Day11ChatResponse.exceeded(
                    dialog.id(), request, overflowMessage(promptTokens, limit),
                    contextTokens, requestTokens,
                    shortTermTokens, workingTokens, longTermTokens,
                    promptTokens, limit,
                    List.copyOf(fullHistory), List.copyOf(candidates), workingEntries, longTermEntries);
        }

        List<ChatCompletionRequest.Message> llmMessages = new ArrayList<>();
        llmMessages.add(new ChatCompletionRequest.Message("system", systemPrompt));
        for (ConversationMessage message : sentHistory) {
            llmMessages.add(new ChatCompletionRequest.Message(message.role(), message.content()));
        }
        llmMessages.add(new ChatCompletionRequest.Message("user", request));

        LlmReply reply = llmClient.complete(CompletionCommand.unconstrained(request), llmMessages);
        long responseTokens = estimator.estimate(reply.content());

        List<ConversationMessage> updated = new ArrayList<>(fullHistory);
        updated.add(ConversationMessage.user(request));
        updated.add(ConversationMessage.assistant(reply.content()));
        Dialog saved = dialog.withMessages(updated);
        store.save(saved);

        return new Day11ChatResponse(
                saved.id(), request, reply.content(), properties.model(),
                updated.size(), reply.elapsedMs(),
                contextTokens, requestTokens,
                shortTermTokens, workingTokens, longTermTokens,
                responseTokens, promptTokens, limit, false,
                List.copyOf(updated), List.copyOf(candidates), workingEntries, longTermEntries);
    }

    public Day11DialogInfo remember(String dialogId, String key, String value, String layerValue) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Ключ не должен быть пустым");
        }
        require(dialogId);
        Day11MemoryLayer layer = Day11MemoryLayer.from(layerValue);
        Day11MemoryEntry entry = new Day11MemoryEntry(key, value, layer, "manual", false, null);
        memoryStore.save(entry);
        return info(dialogId);
    }

    public Day11DialogInfo promote(String dialogId, String key, String toLayerValue) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Ключ не должен быть пустым");
        }
        require(dialogId);
        Day11MemoryLayer toLayer = (toLayerValue == null || toLayerValue.isBlank())
                ? Day11MemoryRules.suggestLayer(key)
                : Day11MemoryLayer.from(toLayerValue);
        Day11MemoryEntry found = memoryStore.find(Day11MemoryLayer.SHORT_TERM, key);
        if (found == null) {
            throw new IllegalArgumentException(
                    "Кандидат с ключом '" + key + "' не найден в краткосрочной памяти");
        }
        memoryStore.delete(Day11MemoryLayer.SHORT_TERM, key);
        memoryStore.save(found.withLayer(toLayer).hardened());
        return info(dialogId);
    }

    public Day11DialogInfo decide(String dialogId, String statement) {
        if (statement == null || statement.isBlank()) {
            throw new IllegalArgumentException("Решение не должно быть пустым");
        }
        require(dialogId);
        long decisions = memoryStore.all(Day11MemoryLayer.LONG_TERM).stream()
                .filter(entry -> entry.key().startsWith("решение:"))
                .count();
        Day11MemoryEntry entry = new Day11MemoryEntry(
                "решение: " + (decisions + 1), statement.trim(),
                Day11MemoryLayer.LONG_TERM, "decision", false, null);
        memoryStore.save(entry);
        return info(dialogId);
    }

    public Day11DialogInfo forget(String dialogId, String layerValue, String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Ключ не должен быть пустым");
        }
        require(dialogId);
        Day11MemoryLayer layer = Day11MemoryLayer.from(layerValue);
        memoryStore.delete(layer, key);
        return info(dialogId);
    }

    public Day11MetricsReport metrics(String dialogId) {
        Dialog dialog = require(dialogId);
        String basePrompt = basePrompt();
        int shortTermWindow = day11Properties.shortTermWindow();
        List<Day11MemoryEntry> workingEntries = memoryStore.all(Day11MemoryLayer.WORKING);
        List<Day11MemoryEntry> longTermEntries = memoryStore.all(Day11MemoryLayer.LONG_TERM);
        String memoryBlock = memoryLayersBlock(shortTermWindow, workingEntries, longTermEntries);
        String systemPrompt = basePrompt + "\n\n" + memoryBlock;
        long fullPromptTokens = estimator.estimate(systemPrompt);

        List<Day11MetricsTurn> turnList = new ArrayList<>();
        List<ConversationMessage> messages = dialog.messages();
        long runningTokens = 0L;
        long runningFullTokens = 0L;
        BigDecimal cumulativeCost = BigDecimal.ZERO;
        BigDecimal cumulativeFullCost = BigDecimal.ZERO;

        for (int i = 0; i + 1 < messages.size(); i += 2) {
            ConversationMessage user = messages.get(i);
            ConversationMessage assistant = messages.get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            int turn = (i / 2) + 1;
            long windowTokens = estimator.estimate(lastN(messages, shortTermWindow, i));
            long prompt = fullPromptTokens + windowTokens + estimator.estimate(user.content());
            long allPrevTokens = estimator.estimate(messages.subList(0, i));
            long fullPrompt = fullPromptTokens + allPrevTokens + estimator.estimate(user.content());
            long responseTokens = estimator.estimate(assistant.content());

            runningTokens += prompt;
            runningFullTokens += fullPrompt;
            BigDecimal turnCost = turnCost(prompt, responseTokens);
            BigDecimal fullTurnCost = turnCost(fullPrompt, responseTokens);
            cumulativeCost = cumulativeCost.add(turnCost);
            cumulativeFullCost = cumulativeFullCost.add(fullTurnCost);
            turnList.add(new Day11MetricsTurn(
                    turn, prompt, runningTokens, runningFullTokens,
                    turnCost, cumulativeCost, cumulativeFullCost));
        }

        Day11MetricsTurn last = turnList.isEmpty() ? zeroTurn() : turnList.getLast();
        return new Day11MetricsReport(
                dialog.id(), day11Properties.contextLimit(),
                day11Properties.inputPrice(), day11Properties.outputPrice(),
                last.cumulativeTokens(), last.cumulativeFullTokens(),
                last.cumulativeCost(), last.cumulativeFullCost(),
                dialog.messages().size(), workingEntries.size(), longTermEntries.size(),
                List.copyOf(turnList));
    }

    public Day11FinishResponse finish(String dialogId) {
        Dialog dialog = require(dialogId);
        if (!dialog.isFinished()) {
            String summary = summarizer.summarize(dialog);
            dialog = dialog.finished(summary, Instant.now());
            store.save(dialog);

            Day11MemoryEntry summaryEntry = new Day11MemoryEntry(
                    "итог:" + dialogId, summary, Day11MemoryLayer.LONG_TERM,
                    "dialog_summary", false, null);
            memoryStore.save(summaryEntry);
        }
        List<Day11MemoryEntry> longTermEntries = memoryStore.all(Day11MemoryLayer.LONG_TERM);
        return new Day11FinishResponse(
                dialog.id(), dialog.finishedAt(), dialog.summary(),
                dialog.messages().size(), longTermEntries.size());
    }

    public List<Dialog> dialogs() {
        if (store instanceof Day11DialogStore day11Store) {
            return day11Store.allDialogs();
        }
        return store.finishedDialogs();
    }

    public Day11DialogInfo get(String dialogId) {
        return info(dialogId);
    }

    private Day11DialogInfo info(String dialogId) {
        Dialog dialog = require(dialogId);
        return new Day11DialogInfo(
                dialog.id(), dialog.createdAt(), dialog.finishedAt(), dialog.summary(),
                dialog.messages().size(), List.copyOf(dialog.messages()),
                memoryStore.all(Day11MemoryLayer.SHORT_TERM),
                memoryStore.all(Day11MemoryLayer.WORKING),
                memoryStore.all(Day11MemoryLayer.LONG_TERM));
    }

    private Dialog require(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        return dialog;
    }

    private String basePrompt() {
        return dialogContext.systemPrompt(List.of());
    }

    private String memoryLayersBlock(
            int shortTermWindow, List<Day11MemoryEntry> working, List<Day11MemoryEntry> longTerm) {
        StringBuilder builder = new StringBuilder();
        builder.append("Слой памяти: Краткосрочная\n");
        builder.append("Краткосрочная память — последние ").append(shortTermWindow)
                .append(" сообщений текущего диалога, переданные далее как история диалога.\n\n");
        builder.append("Слой памяти: Рабочая\n");
        if (working.isEmpty()) {
            builder.append("(пусто)\n\n");
        } else {
            builder.append(blockText(working));
            builder.append("\n");
        }
        builder.append("Слой памяти: Долговременная\n");
        if (longTerm.isEmpty()) {
            builder.append("(пусто)\n\n");
        } else {
            builder.append(blockText(longTerm));
            builder.append("\n");
        }
        builder.append("Используй рабочую память как актуальные данные текущей задачи. ")
                .append("Долговременную память — как профиль, решения и накопленные знания; применяй их всегда.");
        return builder.toString();
    }

    private static String blockText(List<Day11MemoryEntry> entries) {
        StringBuilder builder = new StringBuilder();
        for (Day11MemoryEntry entry : entries) {
            builder.append("- ").append(entry.display()).append("\n");
        }
        return builder.toString();
    }

    private static List<ConversationMessage> lastN(List<ConversationMessage> messages, int n) {
        int from = Math.max(0, messages.size() - n);
        return messages.subList(from, messages.size());
    }

    private static List<ConversationMessage> lastN(
            List<ConversationMessage> messages, int n, int upto) {
        int end = Math.min(upto, messages.size());
        int from = Math.max(0, end - n);
        return messages.subList(from, end);
    }

    private static String overflowMessage(long promptTokens, long limit) {
        return "Диалог превысил контекстное окно модели: " + promptTokens + " токенов при лимите " + limit
                + ". Вопрос не отправлен в модель — она бы вернула ошибку переполнения "
                + "(context_length_exceeded). Уменьшите контекст или завершите диалог и начните новый.";
    }

    private BigDecimal turnCost(long promptTokens, long responseTokens) {
        BigDecimal prompt = BigDecimal.valueOf(promptTokens).divide(MILLION);
        BigDecimal response = BigDecimal.valueOf(responseTokens).divide(MILLION);
        return prompt.multiply(day11Properties.inputPrice())
                .add(response.multiply(day11Properties.outputPrice()));
    }

    private static Day11MetricsTurn zeroTurn() {
        return new Day11MetricsTurn(0, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}