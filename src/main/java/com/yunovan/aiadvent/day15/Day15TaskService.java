package com.yunovan.aiadvent.day15;

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
public class Day15TaskService {

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day15Properties day15Properties;
    private final Day15TaskStore store;
    private final DialogContext dialogContext;
    private final TokenEstimator estimator;

    public Day15TaskService(
            LlmClient llmClient,
            LlmProperties properties,
            Day15Properties day15Properties,
            Day15TaskStore store,
            DialogContext dialogContext,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day15Properties = day15Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.estimator = estimator;
    }

    public Day15Task create(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Название задачи не должно быть пустым");
        }
        return store.save(Day15Task.create(title.trim()));
    }

    public Day15Task get(String taskId) {
        return store.load(requireId(taskId));
    }

    public List<Day15Task> list() {
        return store.all();
    }

    public Day15TaskState state(String taskId) {
        return require(taskId).state();
    }

    public Day15TaskState transition(String taskId, String targetValue) {
        Day15Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Задача завершена. Начните новую задачу.");
        }
        if (task.paused()) {
            throw new IllegalArgumentException("Задача на паузе. Сначала возобновите её (resume).");
        }
        Day15Stage target = Day15Stage.from(targetValue);
        if (target == null) {
            throw new IllegalArgumentException("Неизвестное состояние: " + targetValue
                    + ". Доступные состояния: " + Day15StateMachine.CHAIN);
        }
        try {
            return saveAfterTransition(task, Day15StateMachine.transition(task.stage(), target));
        } catch (IllegalStateException ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    public Day15TaskState advance(String taskId) {
        Day15Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Задача уже завершена");
        }
        if (task.paused()) {
            throw new IllegalArgumentException("Задача на паузе. Сначала возобновите её (resume).");
        }
        return saveAfterTransition(task, Day15StateMachine.nextForward(task.stage()));
    }

    public Day15TaskState setStep(String taskId, int step) {
        Day15Task task = require(taskId);
        if (step <= 0) {
            throw new IllegalArgumentException("Номер шага должен быть положительным");
        }
        Day15Task updated = task.withStep(step);
        store.save(updated);
        return updated.state();
    }

    public Day15TaskState setExpectedAction(String taskId, String expectedAction) {
        if (expectedAction == null || expectedAction.isBlank()) {
            throw new IllegalArgumentException("Ожидаемое действие не должно быть пустым");
        }
        Day15Task task = require(taskId);
        Day15Task updated = task.withExpectedAction(expectedAction.trim());
        store.save(updated);
        return updated.state();
    }

    public Day15TaskState addNote(String taskId, String note) {
        if (note == null || note.isBlank()) {
            throw new IllegalArgumentException("Заметка не должна быть пустой");
        }
        Day15Task task = require(taskId);
        Day15Task updated = task.withNote(note.trim());
        store.save(updated);
        return updated.state();
    }

    public Day15TaskState pause(String taskId) {
        Day15Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Завершённую задачу нельзя поставить на паузу");
        }
        Day15Task updated = task.withPaused(true);
        store.save(updated);
        return updated.state();
    }

    public Day15TaskState resume(String taskId) {
        Day15Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Завершённую задачу нельзя продолжить");
        }
        Day15Task updated = task.withPaused(false);
        store.save(updated);
        return updated.state();
    }

    public Day15ContinueResponse continueTask(String taskId, String userRequest, Long contextLimitOverride) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("Запрос не должен быть пустым");
        }
        Day15Task task = require(taskId);
        if (task.isFinished()) {
            throw new IllegalArgumentException("Задача завершена. Начните новую задачу.");
        }
        if (task.paused()) {
            throw new IllegalArgumentException("Задача на паузе. Сначала возобновите её (resume).");
        }
        String request = userRequest.trim();
        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day15Properties.contextLimit();

        String base = dialogContext.systemPrompt(List.of());
        String stateBlock = Day15TaskPrompt.taskBlock(task);
        String systemPrompt = base + "\n\n" + stateBlock;

        int window = day15Properties.shortTermWindow();
        List<Day15TaskMessage> history = task.history();
        List<Day15TaskMessage> sentHistory = lastN(history, window);

        long contextTokens = estimator.estimate(base + "\n\n" + stateBlock);
        long requestTokens = estimator.estimate(request);
        long historyTokens = sentHistory.stream()
                .map(Day15TaskMessage::content)
                .mapToLong(estimator::estimate)
                .sum();
        long promptTokens = contextTokens + requestTokens + historyTokens;

        if (promptTokens > limit) {
            return Day15ContinueResponse.exceeded(
                    task.id(), request, overflowMessage(promptTokens, limit),
                    contextTokens, requestTokens, historyTokens, promptTokens, limit, task.state());
        }

        List<ChatCompletionRequest.Message> llmMessages = new ArrayList<>();
        llmMessages.add(new ChatCompletionRequest.Message("system", systemPrompt));
        for (Day15TaskMessage message : sentHistory) {
            llmMessages.add(new ChatCompletionRequest.Message(message.role(), message.content()));
        }
        llmMessages.add(new ChatCompletionRequest.Message("user", request));

        LlmReply reply = llmClient.complete(CompletionCommand.unconstrained(request), llmMessages);
        long responseTokens = estimator.estimate(reply.content());

        Day15Task updated = task
                .withMessage(new Day15TaskMessage("user", request))
                .withMessage(new Day15TaskMessage("assistant", reply.content()));
        store.save(updated);

        return new Day15ContinueResponse(
                updated.id(), request, reply.content(), properties.model(),
                reply.elapsedMs(), contextTokens, requestTokens, historyTokens,
                responseTokens, promptTokens, limit, false, updated.state());
    }

    private Day15TaskState saveAfterTransition(Day15Task task, Day15Stage target) {
        Day15Task updated = task.withStage(target);
        if (Day15StateMachine.isTerminal(target)) {
            updated = updated.withFinishedAt(Instant.now());
        }
        store.save(updated);
        return updated.state();
    }

    private Day15Task require(String taskId) {
        String id = requireId(taskId);
        Day15Task task = store.load(id);
        if (task == null) {
            throw new Day15TaskNotFoundException(id);
        }
        return task;
    }

    private static String requireId(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("taskId не должен быть пустым");
        }
        return taskId.trim();
    }

    private static List<Day15TaskMessage> lastN(List<Day15TaskMessage> messages, int n) {
        int from = Math.max(0, messages.size() - n);
        return messages.subList(from, messages.size());
    }

    private static String overflowMessage(long promptTokens, long limit) {
        return "Запрос превысил контекстное окно: " + promptTokens + " токенов при лимите " + limit
                + ". Запрос не отправлен в модель. Завершите задачу или начните новую.";
    }
}