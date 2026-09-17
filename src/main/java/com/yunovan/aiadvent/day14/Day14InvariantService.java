package com.yunovan.aiadvent.day14;

import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.day08.TokenEstimator;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Day14InvariantService {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day14Properties day14Properties;
    private final Day14InvariantStore store;
    private final DialogContext dialogContext;
    private final TokenEstimator estimator;

    public Day14InvariantService(
            LlmClient llmClient,
            LlmProperties properties,
            Day14Properties day14Properties,
            Day14InvariantStore store,
            DialogContext dialogContext,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day14Properties = day14Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.estimator = estimator;
    }

    public Day14Invariant create(String categoryValue, String title, String description) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Заголовок инварианта не должен быть пустым");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Описание инварианта не должно быть пустым");
        }
        Day14Category category = Day14Category.from(categoryValue);
        if (category == null) {
            throw new IllegalArgumentException("Неизвестная категория инварианта: " + categoryValue
                    + " (доступно: архитектура, решение, стек, бизнес)");
        }
        return store.save(Day14Invariant.create(category, title, description));
    }

    public List<Day14Invariant> list() {
        return store.all();
    }

    public List<Day14Invariant> active() {
        return store.all().stream().filter(Day14Invariant::active).toList();
    }

    public Day14Invariant get(String invariantId) {
        Day14Invariant invariant = store.load(requireId(invariantId));
        if (invariant == null) {
            throw new Day14InvariantNotFoundException(invariantId);
        }
        return invariant;
    }

    public Day14Invariant deactivate(String invariantId) {
        String id = requireId(invariantId);
        Day14Invariant invariant = store.load(id);
        if (invariant == null) {
            throw new Day14InvariantNotFoundException(id);
        }
        return store.save(invariant.withActive(false));
    }

    public boolean delete(String invariantId) {
        return store.delete(requireId(invariantId));
    }

    public Day14AdviseResponse advise(String requestValue, Long contextLimitOverride) {
        if (requestValue == null || requestValue.isBlank()) {
            throw new IllegalArgumentException("Запрос не должен быть пустым");
        }
        String request = requestValue.trim();
        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day14Properties.contextLimit();
        List<Day14Invariant> invariants = active();

        String base = dialogContext.systemPrompt(List.of());
        String invariantsBlock = Day14InvariantPrompt.invariantsBlock(invariants);
        String systemPrompt = "Отвечай на русском.\n\n" + base + "\n\n" + invariantsBlock;

        long contextTokens = estimator.estimate(systemPrompt);
        long requestTokens = estimator.estimate(request);
        long promptTokens = contextTokens + requestTokens;

        if (promptTokens > limit) {
            return Day14AdviseResponse.exceeded(
                    "c" + System.currentTimeMillis(),
                    request,
                    overflowMessage(promptTokens, limit),
                    contextTokens, requestTokens, 0L,
                    promptTokens, limit, invariants);
        }

        List<ChatCompletionRequest.Message> llmMessages = new ArrayList<>();
        llmMessages.add(new ChatCompletionRequest.Message("system", systemPrompt));
        llmMessages.add(new ChatCompletionRequest.Message("user", request));

        LlmReply reply = llmClient.complete(CompletionCommand.unconstrained(request), llmMessages);
        long responseTokens = estimator.estimate(reply.content());

        return new Day14AdviseResponse(
                "c" + System.currentTimeMillis(),
                request,
                reply.content(),
                properties.model(),
                reply.elapsedMs(),
                contextTokens,
                requestTokens,
                0L,
                responseTokens,
                promptTokens,
                limit,
                false,
                invariants);
    }

    public String statusLine() {
        List<Day14Invariant> invariants = store.all();
        long activeCount = invariants.stream().filter(Day14Invariant::active).count();
        StringBuilder builder = new StringBuilder();
        builder.append("Инварианты: всего ").append(invariants.size())
                .append(", активных ").append(activeCount).append("\n");
        if (!invariants.isEmpty()) {
            for (Day14Invariant invariant : invariants) {
                builder.append("- ").append(invariant.category().display())
                        .append(invariant.active() ? "" : " [выключен]")
                        .append(" — ").append(invariant.title()).append(": ")
                        .append(invariant.description()).append("\n");
            }
        }
        builder.append("Последний конфликт-чек: ")
                .append(TIME.format(Instant.now())).append(" (запрос проходит через проверку инвариантов)");
        return builder.toString();
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id не должен быть пустым");
        }
        return id.trim();
    }

    private static String overflowMessage(long promptTokens, long limit) {
        return "Запрос превысил контекстное окно: " + promptTokens + " токенов при лимите " + limit
                + ". Запрос не отправлен в модель. Проверка инвариантов возможна вручную.";
    }
}