package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
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
public class Day10DialogService {

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day10Properties day10Properties;
    private final Day10FileDialogStore store;
    private final DialogContext dialogContext;
    private final DialogSummarizer summarizer;
    private final Day10FactExtractor factExtractor;
    private final TokenEstimator estimator;

    public Day10DialogService(
            LlmClient llmClient,
            LlmProperties properties,
            Day10Properties day10Properties,
            @Qualifier("day10DialogStore") Day10FileDialogStore store,
            DialogContext dialogContext,
            DialogSummarizer summarizer,
            Day10FactExtractor factExtractor,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day10Properties = day10Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.summarizer = summarizer;
        this.factExtractor = factExtractor;
        this.estimator = estimator;
    }

    public Day10StartResponse start(String strategyValue, Integer windowOverride) {
        Day10Strategy strategy = Day10Strategy.from(strategyValue);
        int windowSize = windowOverride != null && windowOverride > 0
                ? windowOverride
                : day10Properties.defaultWindow();
        Day10Dialog dialog = store.create(strategy, windowSize);
        return new Day10StartResponse(
                dialog.id(), dialog.createdAt(), dialog.strategy(), dialog.windowSize(),
                dialog.facts(), dialog.branches(), dialog.activeBranchId(), memory());
    }

    public Day10ChatResponse chat(
            String dialogId, String userRequest, Long contextLimitOverride, Integer windowOverride) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        Day10Dialog dialog = require(dialogId);
        if (dialog.isFinished()) {
            throw new IllegalArgumentException("Диалог завершён. Начните новый диалог.");
        }
        if (dialog.strategy() != Day10Strategy.BRANCHING && windowOverride != null && windowOverride > 0) {
            dialog = dialog.withWindow(windowOverride);
        }
        String request = userRequest.trim();
        if (dialog.strategy() == Day10Strategy.FACTS) {
            dialog = dialog.withFacts(mergeExtracted(dialog.facts(), factExtractor.extract(request)));
        }

        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day10Properties.contextLimit();

        String basePrompt = basePrompt(dialog);
        String systemPrompt = systemPromptFor(dialog, basePrompt);
        List<ConversationMessage> fullHistory = dialog.activeMessages();
        List<ConversationMessage> sentHistory = historyFor(dialog, fullHistory, fullHistory.size());

        long contextTokens = estimator.estimate(systemPrompt);
        long requestTokens = estimator.estimate(request);
        long historyTokens = estimator.estimate(sentHistory);
        long fullHistoryTokens = estimator.estimate(fullHistory);
        long promptTokens = contextTokens + historyTokens + requestTokens;
        long fullPromptTokens = estimator.estimate(basePrompt) + fullHistoryTokens + requestTokens;
        long savedTokens = dialog.strategy() == Day10Strategy.BRANCHING
                ? 0L
                : Math.max(0L, fullPromptTokens - promptTokens);

        if (promptTokens > limit) {
            return Day10ChatResponse.exceeded(
                    dialog.id(), request, overflowMessage(promptTokens, limit),
                    dialog.strategy(), dialog.windowSize(), dialog.facts(), dialog.branches(),
                    dialog.activeBranchId(),
                    contextTokens, requestTokens, historyTokens, fullHistoryTokens,
                    promptTokens, fullPromptTokens, savedTokens, limit,
                    List.copyOf(fullHistory), memory());
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
        Day10Dialog saved = dialog.withBranchMessages(dialog.activeBranchId(), updated);
        store.save(saved);

        List<Day10TurnBudget> budgetHistory = budgets(saved, basePrompt);
        Day10TurnBudget current = budgetHistory.isEmpty() ? zeroBudget() : budgetHistory.getLast();

        return new Day10ChatResponse(
                saved.id(), request, reply.content(), properties.model(),
                updated.size(), reply.elapsedMs(),
                saved.strategy(), saved.windowSize(), saved.facts(), saved.branches(), saved.activeBranchId(),
                contextTokens, requestTokens, historyTokens, fullHistoryTokens,
                promptTokens, fullPromptTokens, savedTokens, responseTokens,
                current.turnCost(), current.cumulativeCost(), current.cumulativeFullCost(),
                limit, false, List.copyOf(updated), memory());
    }

    public Day10DialogInfo addFact(String dialogId, String key, String value, Boolean active) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Ключ факта не должен быть пустым");
        }
        Day10Dialog dialog = require(dialogId);
        boolean activeFlag = active == null || active;
        Day10Dialog saved = dialog.withFacts(upsertFact(dialog.facts(), new Day10Fact(key, value, activeFlag)));
        store.save(saved);
        return get(dialogId);
    }

    public Day10DialogInfo checkpoint(String dialogId) {
        Day10Dialog dialog = require(dialogId);
        if (dialog.strategy() != Day10Strategy.BRANCHING) {
            throw new IllegalArgumentException("Checkpoint доступен только для стратегии Branching");
        }
        int index = dialog.activeBranch().messages().size();
        store.save(dialog.withCheckpoint(index));
        return get(dialogId);
    }

    public Day10DialogInfo createBranch(String dialogId) {
        Day10Dialog dialog = require(dialogId);
        if (dialog.strategy() != Day10Strategy.BRANCHING) {
            throw new IllegalArgumentException("Ветки доступны только для стратегии Branching");
        }
        if (dialog.checkpointMessageIndex() == null) {
            throw new IllegalArgumentException("Сначала сохраните checkpoint, затем создавайте ветку");
        }
        Day10Branch parent = dialog.activeBranch();
        int number = dialog.branches().size() + 1;
        String id = "b" + number;
        while (branchExists(dialog, id)) {
            number++;
            id = "b" + number;
        }
        Day10Branch branch = Day10Branch.fork(id, "Ветка " + number, parent, dialog.checkpointMessageIndex());
        store.save(dialog.withNewBranch(branch));
        return get(dialogId);
    }

    public Day10DialogInfo switchBranch(String dialogId, String branchId) {
        Day10Dialog dialog = require(dialogId);
        store.save(dialog.withActiveBranch(branchId));
        return get(dialogId);
    }

    public Day10GrowthReport metrics(String dialogId) {
        Day10Dialog dialog = require(dialogId);
        String basePrompt = basePrompt(dialog);
        List<Day10TurnBudget> budgets = budgets(dialog, basePrompt);
        List<Day10GrowthTurn> turns = new ArrayList<>();
        for (Day10TurnBudget budget : budgets) {
            turns.add(new Day10GrowthTurn(
                    budget.turn(), budget.sentTokens(), budget.responseTokens(),
                    budget.cumulativeSent(), budget.turnCost(), budget.cumulativeCost()));
        }
        Day10TurnBudget last = budgets.isEmpty() ? zeroBudget() : budgets.getLast();
        return new Day10GrowthReport(
                dialog.id(), dialog.strategy(), day10Properties.contextLimit(),
                day10Properties.inputPrice(), day10Properties.outputPrice(),
                last.cumulativeSent(), last.cumulativeFull(),
                last.cumulativeCost(), last.cumulativeFullCost(),
                dialog.windowSize(), dialog.facts().size(), dialog.branches().size(),
                dialog.activeBranchId(), List.copyOf(turns));
    }

    public List<Day10DialogSummary> dialogs() {
        return store.allDialogs().stream()
                .map(dialog -> {
                    List<Day10TurnBudget> budgets = budgets(dialog, basePrompt(dialog));
                    Day10TurnBudget last = budgets.isEmpty() ? zeroBudget() : budgets.getLast();
                    return new Day10DialogSummary(
                            dialog.id(), dialog.createdAt(), dialog.finishedAt(), dialog.summary(),
                            dialog.strategy(), dialog.windowSize(), dialog.activeMessages().size(),
                            dialog.facts().size(), dialog.branches().size(),
                            last.cumulativeSent(), last.cumulativeFull(),
                            last.cumulativeCost(), last.cumulativeFullCost());
                })
                .toList();
    }

    public Day10DialogInfo get(String dialogId) {
        Day10Dialog dialog = require(dialogId);
        return new Day10DialogInfo(
                dialog.id(), dialog.createdAt(), dialog.finishedAt(), dialog.summary(),
                dialog.strategy(), dialog.windowSize(), dialog.facts(), dialog.branches(),
                dialog.activeBranchId(), dialog.activeMessages().size(), List.copyOf(dialog.activeMessages()));
    }

    public Day10FinishResponse finish(String dialogId) {
        Day10Dialog dialog = require(dialogId);
        if (!dialog.isFinished()) {
            String summary = summarizer.summarize(toAgentDialog(dialog));
            dialog = dialog.finished(summary, Instant.now());
            store.save(dialog);
        }
        return new Day10FinishResponse(
                dialog.id(), dialog.finishedAt(), dialog.summary(), dialog.activeMessages().size());
    }

    private Day10Dialog require(String dialogId) {
        Day10Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        return dialog;
    }

    private List<Day10TurnBudget> budgets(Day10Dialog dialog, String basePrompt) {
        List<ConversationMessage> messages = dialog.activeMessages();
        long contextTokens = estimator.estimate(systemPromptFor(dialog, basePrompt));
        long baseTokens = estimator.estimate(basePrompt);
        long runningSent = 0L;
        long runningFull = 0L;
        BigDecimal cumulativeCost = BigDecimal.ZERO;
        BigDecimal cumulativeFullCost = BigDecimal.ZERO;
        List<Day10TurnBudget> result = new ArrayList<>();

        for (int i = 0; i + 1 < messages.size(); i += 2) {
            ConversationMessage user = messages.get(i);
            ConversationMessage assistant = messages.get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            int turn = (i / 2) + 1;
            long sentTokens = contextTokens
                    + estimator.estimate(historyFor(dialog, messages, i))
                    + estimator.estimate(user.content());
            long fullTokens = baseTokens
                    + estimator.estimate(messages.subList(0, i))
                    + estimator.estimate(user.content());
            long responseTokens = estimator.estimate(assistant.content());

            runningSent += sentTokens;
            runningFull += fullTokens;
            BigDecimal turnCost = turnCost(sentTokens, responseTokens);
            BigDecimal fullTurnCost = turnCost(fullTokens, responseTokens);
            cumulativeCost = cumulativeCost.add(turnCost);
            cumulativeFullCost = cumulativeFullCost.add(fullTurnCost);
            result.add(new Day10TurnBudget(
                    turn, sentTokens, fullTokens, responseTokens,
                    runningSent, runningFull, turnCost, cumulativeCost, cumulativeFullCost));
        }
        return result;
    }

    private static List<ConversationMessage> historyFor(
            Day10Dialog dialog, List<ConversationMessage> messages, int upto) {
        int end = Math.min(upto, messages.size());
        if (dialog.strategy() == Day10Strategy.BRANCHING) {
            return messages.subList(0, end);
        }
        int from = Math.max(0, end - dialog.windowSize());
        return messages.subList(from, end);
    }

    private String basePrompt(Day10Dialog dialog) {
        return dialogContext.systemPrompt(previousAgentDialogs(dialog.id()));
    }

    private String systemPromptFor(Day10Dialog dialog, String basePrompt) {
        StringBuilder builder = new StringBuilder(basePrompt);
        switch (dialog.strategy()) {
            case SLIDING_WINDOW -> builder.append("\n\nСтратегия контекста: Sliding Window. ")
                    .append("В модели есть только последние ").append(dialog.windowSize())
                    .append(" сообщений; всё, что было раньше, из контекста отброшено. ")
                    .append("Если не хватает деталей — честно скажи об этом.");
            case FACTS -> {
                builder.append("\n\nСтратегия контекста: Sticky Facts. ")
                        .append("Ниже — блок фактов (ключ-значение), которые ты помнишь о разговоре, ")
                        .append("плюс последние ").append(dialog.windowSize()).append(" сообщений.");
                builder.append("\n\nФакты:");
                List<Day10Fact> active = dialog.facts().stream().filter(Day10Fact::usable).toList();
                if (active.isEmpty()) {
                    builder.append("\nпока нет.");
                } else {
                    for (Day10Fact fact : active) {
                        builder.append("\n- ").append(fact.display());
                    }
                }
            }
            case BRANCHING -> builder.append("\n\nСтратегия контекста: Branching. Ты ведёшь ветку «")
                    .append(dialog.activeBranch().name())
                    .append("»; её история сохранена полностью. Ты не видишь, что происходит в других ветках.");
        }
        return builder.toString();
    }

    private List<Dialog> previousAgentDialogs(String exceptId) {
        return store.finishedDialogs().stream()
                .filter(dialog -> !dialog.id().equals(exceptId))
                .map(Day10DialogService::toAgentDialog)
                .toList();
    }

    private static Dialog toAgentDialog(Day10Dialog dialog) {
        return new Dialog(
                dialog.id(), dialog.createdAt(), dialog.finishedAt(), dialog.summary(),
                null, 0, dialog.activeMessages());
    }

    private List<DialogMemory> memory() {
        return store.finishedDialogs().stream()
                .map(dialog -> new DialogMemory(dialog.id(), dialog.summary()))
                .toList();
    }

    private static List<Day10Fact> mergeExtracted(List<Day10Fact> existing, List<Day10Fact> extracted) {
        List<Day10Fact> result = new ArrayList<>(existing);
        for (Day10Fact fact : extracted) {
            boolean replaced = false;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i).key().equalsIgnoreCase(fact.key())) {
                    result.set(i, result.get(i).withValue(fact.value()));
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                result.add(fact);
            }
        }
        return result;
    }

    private static List<Day10Fact> upsertFact(List<Day10Fact> existing, Day10Fact fact) {
        List<Day10Fact> result = new ArrayList<>(existing);
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).key().equalsIgnoreCase(fact.key())) {
                result.set(i, fact);
                return result;
            }
        }
        result.add(fact);
        return result;
    }

    private static boolean branchExists(Day10Dialog dialog, String branchId) {
        return dialog.branches().stream().anyMatch(branch -> branch.id().equals(branchId));
    }

    private BigDecimal turnCost(long promptTokens, long responseTokens) {
        BigDecimal prompt = BigDecimal.valueOf(promptTokens).divide(MILLION);
        BigDecimal response = BigDecimal.valueOf(responseTokens).divide(MILLION);
        return prompt.multiply(day10Properties.inputPrice())
                .add(response.multiply(day10Properties.outputPrice()));
    }

    private static Day10TurnBudget zeroBudget() {
        return new Day10TurnBudget(0, 0L, 0L, 0L, 0L, 0L, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static String overflowMessage(long promptTokens, long limit) {
        return "Диалог превысил контекстное окно модели: " + promptTokens + " токенов при лимите " + limit
                + ". Вопрос не отправлен в модель — она бы вернула ошибку переполнения "
                + "(context_length_exceeded). Уменьшите окно (Sliding Window / Facts) "
                + "или завершите диалог и начните новый.";
    }

    private record Day10TurnBudget(
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