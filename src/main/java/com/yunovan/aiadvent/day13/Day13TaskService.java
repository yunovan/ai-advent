package com.yunovan.aiadvent.day13;

import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.day08.TokenEstimator;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Day13TaskService {

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day13Properties day13Properties;
    private final Day13TaskStore store;
    private final DialogContext dialogContext;
    private final TokenEstimator estimator;

    public Day13TaskService(
            LlmClient llmClient,
            LlmProperties properties,
            Day13Properties day13Properties,
            Day13TaskStore store,
            DialogContext dialogContext,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day13Properties = day13Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.estimator = estimator;
    }

    public Day13Task create(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Название задачи не должно быть пустым");
        }
        Day13Task task = store.save(Day13Task.create(title.trim()));
        store.save(task);
        return task;
    }

    public Day13Task get(String taskId) {
        return store.load(requireId(taskId));
    }

    public List<Day13Task> list() {
        return store.all();
    }

    public Day13TaskState state(String taskId) {
        return require(taskId).state();
    }

    public Day13TaskState advance(String taskId) {
        Day13Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Задача уже завершена");
        }
        Day13Stage next = Day13StateMachine.next(task.stage());
        Day13Task updated = task.withStage(next);
        if (Day13StateMachine.isTerminal(next)) {
            updated = updated.withFinishedAt(Instant.now());
        }
        store.save(updated);
        return updated.state();
    }

    public Day13TaskState setStep(String taskId, int step) {
        Day13Task task = require(taskId);
        if (step <= 0) {
            throw new IllegalArgumentException("Номер шага должен быть положительным");
        }
        Day13Task updated = task.withStep(step);
        store.save(updated);
        return updated.state();
    }

    public Day13TaskState setExpectedAction(String taskId, String expectedAction) {
        if (expectedAction == null || expectedAction.isBlank()) {
            throw new IllegalArgumentException("Ожидаемое действие не должно быть пустым");
        }
        Day13Task task = require(taskId);
        Day13Task updated = task.withExpectedAction(expectedAction.trim());
        store.save(updated);
        return updated.state();
    }

    public Day13TaskState addNote(String taskId, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Заметка не должна быть пустой");
        }
        Day13Task task = require(taskId);
        Day13Task updated = task.withNote(note.trim());
        store.save(updated);
        return updated.state();
    }

    public Day13TaskState pause(String taskId) {
        Day13Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Завершённую задачу нельзя поставить на паузу");
        }
        Day13Task updated = task.withPaused(true);
        store.save(updated);
        return updated.state();
    }

    public Day13TaskState resume(String taskId) {
        Day13Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Завершённую задачу нельзя продолжить");
        }
        Day13Task updated = task.withPaused(false);
        store.save(updated);
        return updated.state();
    }

    public Day13ContinueResponse continueTask(String taskId, String userRequest, Long contextLimitOverride) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("Запрос не должен быть пустым");
        }
        Day13Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Задача завершена. Начните новую задачу.");
        }
        if (task.paused()) {
            throw new IllegalArgumentException("Задача на паузе. Сначала возобновите её (resume).");
        }
        String request = userRequest.trim();
        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day13Properties.contextLimit();

        String base = dialogContext.systemPrompt(List.of());
        String stateBlock = Day13TaskPrompt.taskBlock(task);
        String systemPrompt = base + "\n\n" + stateBlock;

        int window = day13Properties.shortTermWindow();
        List<Day13TaskMessage> history = task.history();
        List<Day13TaskMessage> sentHistory = lastN(history, window);

        long contextTokens = estimator.estimate(base + "\n\n" + stateBlock);
        long requestTokens = estimator.estimate(request);
        long historyTokens = sentHistory.stream()
                .map(Day13TaskMessage::content)
                .mapToLong(estimator::estimate)
                .sum();
        long promptTokens = contextTokens + requestTokens + historyTokens;

        if (promptTokens > limit) {
            return Day13ContinueResponse.exceeded(
                    task.id(), request, overflowMessage(promptTokens, limit),
                    contextTokens, requestTokens, historyTokens, promptTokens, limit, task.state());
        }

        List<ChatCompletionRequest.Message> llmMessages = new ArrayList<>();
        llmMessages.add(new ChatCompletionRequest.Message("system", systemPrompt));
        for (Day13TaskMessage message : sentHistory) {
            llmMessages.add(new ChatCompletionRequest.Message(message.role(), message.content()));
        }
        llmMessages.add(new ChatCompletionRequest.Message("user", request));

        LlmReply reply = llmClient.complete(CompletionCommand.unconstrained(request), llmMessages);
        long responseTokens = estimator.estimate(reply.content());

        Day13Task updated = task
                .withMessage(new Day13TaskMessage("user", request))
                .withMessage(new Day13TaskMessage("assistant", reply.content()));
        store.save(updated);

        return new Day13ContinueResponse(
                updated.id(), request, reply.content(), properties.model(),
                reply.elapsedMs(), contextTokens, requestTokens, historyTokens,
                responseTokens, promptTokens, limit, false, updated.state());
    }

    private Day13Task require(String taskId) {
        String id = requireId(taskId);
        Day13Task task = store.load(id);
        if (task == null) {
            throw new Day13TaskNotFoundException(id);
        }
        return task;
    }

    private static String requireId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId не должен быть пустым");
        }
        return taskId.trim();
    }

    private static List<Day13TaskMessage> lastN(List<Day13TaskMessage> messages, int n) {
        int from = Math.max(0, messages.size() - n);
        return messages.subList(from, messages.size());
    }

    private static String overflowMessage(long promptTokens, long limit) {
        return "Запрос превысил контекстное окно: " + promptTokens + " токенов при лимите " + limit
                + ". Запрос не отправлен в модель. Завершите задачу или начните новую.";
    }
}