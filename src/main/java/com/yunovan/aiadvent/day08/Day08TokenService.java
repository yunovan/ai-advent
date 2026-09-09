package com.yunovan.aiadvent.day08;

import com.yunovan.aiadvent.agent.Conversation;
import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.ContextualChatAgent;
import com.yunovan.aiadvent.agent.store.ConversationStore;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Агент дня 8: считает токены для запроса, всей истории и ответа модели,
 * оценивает стоимость диалога и не даёт диалогу выйти за контекстное окно модели.
 */
@Service
public class Day08TokenService {

    public static final String SYSTEM_PROMPT =
            "Ты — AI-агент с памятью. Отвечай кратко, опираясь на весь предыдущий диалог.";

    private static final BigDecimal MILLION = new BigDecimal("1000000");

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day8Properties day8Properties;
    private final ConversationStore store;
    private final TokenEstimator estimator;

    public Day08TokenService(
            LlmClient llmClient,
            LlmProperties properties,
            Day8Properties day8Properties,
            ConversationStore store,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day8Properties = day8Properties;
        this.store = store;
        this.estimator = estimator;
    }

    public Day08ChatResponse chat(String sessionId, String userRequest, Long contextLimitOverride) {
        String sid = ContextualChatAgent.normalize(sessionId);
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        String request = userRequest.trim();
        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day8Properties.contextLimit();

        Conversation conversation = store.load(sid);
        List<ConversationMessage> history = conversation.messages();
        long historyTokens = estimator.estimate(history);
        long requestTokens = estimator.estimate(request);

        List<ConversationMessage> messages = new ArrayList<>(history);
        if (messages.isEmpty()) {
            messages.add(ConversationMessage.system(SYSTEM_PROMPT));
        }
        messages.add(ConversationMessage.user(request));

        long promptTokens = estimator.estimate(messages);

        if (promptTokens > limit) {
            return Day08ChatResponse.exceeded(
                    sid,
                    request,
                    overflowMessage(promptTokens, limit),
                    requestTokens,
                    historyTokens,
                    promptTokens,
                    limit,
                    history);
        }

        LlmReply reply = llmClient.complete(
                CompletionCommand.unconstrained(request), toChatMessages(messages));

        long responseTokens = estimator.estimate(reply.content());
        messages.add(ConversationMessage.assistant(reply.content()));
        store.save(conversation.withMessages(messages));

        return new Day08ChatResponse(
                sid,
                request,
                reply.content(),
                properties.model(),
                messages.size(),
                reply.elapsedMs(),
                requestTokens,
                historyTokens,
                promptTokens,
                responseTokens,
                reply.promptTokens(),
                reply.completionTokens(),
                reply.totalTokens(),
                reply.costUsd(),
                estimatedTurnCost(promptTokens, responseTokens),
                estimatedCumulativeCost(messages),
                limit,
                false,
                List.copyOf(messages));
    }

    public void reset(String sessionId) {
        store.delete(ContextualChatAgent.normalize(sessionId));
    }

    public Day08GrowthReport metrics(String sessionId) {
        String sid = ContextualChatAgent.normalize(sessionId);
        Conversation conversation = store.load(sid);
        List<ConversationMessage> messages = conversation.messages();
        List<Day08GrowthTurn> turns = new ArrayList<>();
        long runningHistory = messages.isEmpty() ? 0L : estimator.estimate(messages.get(0).content());
        long cumulativeTokens = runningHistory;
        BigDecimal cumulativeCost = BigDecimal.ZERO;
        int turnCount = 0;

        for (int i = 1; i + 1 < messages.size(); i += 2) {
            ConversationMessage user = messages.get(i);
            ConversationMessage assistant = messages.get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            turnCount++;
            long promptTokens = runningHistory + estimator.estimate(user.content());
            long responseTokens = estimator.estimate(assistant.content());
            BigDecimal turnCost = estimatedTurnCost(promptTokens, responseTokens);
            cumulativeCost = cumulativeCost.add(turnCost);
            cumulativeTokens += promptTokens + responseTokens;
            turns.add(new Day08GrowthTurn(
                    turnCount, promptTokens, responseTokens, cumulativeTokens, turnCost, cumulativeCost));
            runningHistory = promptTokens + responseTokens;
        }

        return new Day08GrowthReport(
                sid,
                day8Properties.contextLimit(),
                day8Properties.inputPrice(),
                day8Properties.outputPrice(),
                runningHistory,
                cumulativeCost,
                List.copyOf(turns));
    }

    private BigDecimal estimatedCumulativeCost(List<ConversationMessage> messages) {
        List<ConversationMessage> history = messages;
        long runningHistory = history.isEmpty() ? 0L : estimator.estimate(history.get(0).content());
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int i = 1; i + 1 < history.size(); i += 2) {
            ConversationMessage user = history.get(i);
            ConversationMessage assistant = history.get(i + 1);
            if (!"user".equals(user.role()) || !"assistant".equals(assistant.role())) {
                continue;
            }
            long promptTokens = runningHistory + estimator.estimate(user.content());
            long responseTokens = estimator.estimate(assistant.content());
            cumulative = cumulative.add(estimatedTurnCost(promptTokens, responseTokens));
            runningHistory = promptTokens + responseTokens;
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
                + ". Дальнейшие вопросы не отправляются в модель — она бы вернула ошибку переполнения "
                + "(context_length_exceeded). Начните новую сессию или сбросьте историю: POST /api/day8/reset.";
    }
}