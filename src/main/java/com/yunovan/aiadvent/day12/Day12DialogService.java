package com.yunovan.aiadvent.day12;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.agent.dialog.DialogSummarizer;
import com.yunovan.aiadvent.day08.TokenEstimator;
import com.yunovan.aiadvent.day11.Day11FileMemoryStore;
import com.yunovan.aiadvent.day11.Day11MemoryEntry;
import com.yunovan.aiadvent.day11.Day11MemoryLayer;
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

@Service
public class Day12DialogService {

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final Day12Properties day12Properties;
    private final Day12DialogStore store;
    private final DialogContext dialogContext;
    private final DialogSummarizer summarizer;
    private final Day12ProfileStore profileStore;
    private final Day12DialogProfileStore linkStore;
    private final Day11FileMemoryStore memoryStore;
    private final TokenEstimator estimator;

    public Day12DialogService(
            LlmClient llmClient,
            LlmProperties properties,
            Day12Properties day12Properties,
            Day12DialogStore store,
            DialogContext dialogContext,
            DialogSummarizer summarizer,
            Day12ProfileStore profileStore,
            Day12DialogProfileStore linkStore,
            @Qualifier("day12MemoryStore") Day11FileMemoryStore memoryStore,
            TokenEstimator estimator) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.day12Properties = day12Properties;
        this.store = store;
        this.dialogContext = dialogContext;
        this.summarizer = summarizer;
        this.profileStore = profileStore;
        this.linkStore = linkStore;
        this.memoryStore = memoryStore;
        this.estimator = estimator;
    }

    public Day12StartResponse start(String requestedProfile) {
        profileStore.seedIfEmpty();
        Day12Profile profile = resolveProfile(requestedProfile);
        if (profile == null) {
            profile = defaultProfile();
        }
        Dialog dialog = store.create();
        linkStore.assign(dialog.id(), profile.id());
        return new Day12StartResponse(dialog.id(), dialog.createdAt(), profile, profileStore.all());
    }

    public Day12ChatResponse chat(String dialogId, String userRequest, Long contextLimitOverride) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        profileStore.seedIfEmpty();
        Dialog dialog = require(dialogId);
        if (dialog.isFinished()) {
            throw new IllegalArgumentException("Диалог завершён. Начните новый диалог.");
        }
        String request = userRequest.trim();
        Day12Profile profile = profileFor(dialogId);

        long limit = contextLimitOverride != null && contextLimitOverride > 0
                ? contextLimitOverride
                : day12Properties.contextLimit();

        String base = dialogContext.systemPrompt(List.of());
        String profileBlock = Day12Personalizer.block(profile);
        List<Day11MemoryEntry> workingEntries = memoryStore.all(Day11MemoryLayer.WORKING);
        List<Day11MemoryEntry> longTermEntries = memoryStore.all(Day11MemoryLayer.LONG_TERM);

        int shortTermWindow = day12Properties.shortTermWindow();
        List<ConversationMessage> fullHistory = dialog.messages();
        List<ConversationMessage> sentHistory = lastN(fullHistory, shortTermWindow);

        String memoryBlock = memoryLayersBlock(shortTermWindow, workingEntries, longTermEntries);
        String systemPrompt = base + "\n\n" + profileBlock + "\n\n" + memoryBlock;

        long contextTokens = estimator.estimate(base + "\n\n" + profileBlock);
        long profileTokens = estimator.estimate(profileBlock);
        long workingTokens = estimator.estimate(blockText(workingEntries));
        long longTermTokens = estimator.estimate(blockText(longTermEntries));
        long requestTokens = estimator.estimate(request);
        long shortTermTokens = estimator.estimate(sentHistory);
        long promptTokens = contextTokens + shortTermTokens + requestTokens + workingTokens + longTermTokens;

        if (promptTokens > limit) {
            return Day12ChatResponse.exceeded(
                    dialog.id(), request, overflowMessage(promptTokens, limit),
                    contextTokens, requestTokens,
                    profileTokens, workingTokens, longTermTokens,
                    promptTokens, limit, profile,
                    List.copyOf(fullHistory), workingEntries, longTermEntries);
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

        return new Day12ChatResponse(
                saved.id(), request, reply.content(), properties.model(),
                updated.size(), reply.elapsedMs(),
                contextTokens, requestTokens,
                profileTokens, workingTokens, longTermTokens,
                responseTokens, promptTokens, limit, false,
                profile, List.copyOf(updated), workingEntries, longTermEntries);
    }

    public Day12DialogInfo setProfile(String dialogId, String profileId) {
        profileStore.seedIfEmpty();
        require(dialogId);
        Day12Profile profile = resolveProfile(profileId);
        if (profile == null) {
            throw new IllegalArgumentException("Профиль '" + profileId + "' не найден");
        }
        linkStore.assign(dialogId, profile.id());
        return info(dialogId);
    }

    public Day12DialogInfo remember(String dialogId, String key, String value, String layerValue) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Ключ не должен быть пустым");
        }
        require(dialogId);
        Day11MemoryLayer layer = Day11MemoryLayer.from(layerValue);
        Day11MemoryEntry entry = new Day11MemoryEntry(key, value, layer, "manual", false, null);
        memoryStore.save(entry);
        return info(dialogId);
    }

    public Day12Profile createProfile(
            String name, String style, String format, List<String> restrictions, String notes) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Имя профиля не должно быть пустым");
        }
        return profileStore.create(name, style, format, restrictions, notes);
    }

    public List<Day12Profile> profiles() {
        profileStore.seedIfEmpty();
        return profileStore.all();
    }

    public Day12Profile profile(String idOrName) {
        profileStore.seedIfEmpty();
        return resolveProfile(idOrName);
    }

    public Day12DialogInfo get(String dialogId) {
        profileStore.seedIfEmpty();
        return info(dialogId);
    }

    public List<Day12DialogInfo> dialogs() {
        profileStore.seedIfEmpty();
        return store.allDialogs().stream()
                .map(dialog -> info(dialog.id()))
                .toList();
    }

    public Day12FinishResponse finish(String dialogId) {
        profileStore.seedIfEmpty();
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
        Day12Profile profile = profileFor(dialogId);
        List<Day11MemoryEntry> longTermEntries = memoryStore.all(Day11MemoryLayer.LONG_TERM);
        return new Day12FinishResponse(
                dialog.id(), dialog.finishedAt(), dialog.summary(),
                dialog.messages().size(),
                profile == null ? null : profile.id(),
                longTermEntries.size());
    }

    private Day12DialogInfo info(String dialogId) {
        Dialog dialog = require(dialogId);
        return new Day12DialogInfo(
                dialog.id(), dialog.createdAt(), dialog.finishedAt(), dialog.summary(),
                dialog.messages().size(), List.copyOf(dialog.messages()),
                profileFor(dialogId),
                memoryStore.all(Day11MemoryLayer.WORKING),
                memoryStore.all(Day11MemoryLayer.LONG_TERM));
    }

    private Day12Profile profileFor(String dialogId) {
        String profileId = linkStore.profileIdFor(dialogId);
        Day12Profile profile = resolveProfile(profileId);
        return profile != null ? profile : defaultProfile();
    }

    private Day12Profile resolveProfile(String idOrName) {
        if (idOrName == null || idOrName.isBlank()) {
            return null;
        }
        Day12Profile byId = profileStore.find(idOrName.trim());
        if (byId != null) {
            return byId;
        }
        return profileStore.findByName(idOrName.trim());
    }

    private Day12Profile defaultProfile() {
        List<Day12Profile> profiles = profileStore.all();
        return profiles.isEmpty() ? null : profiles.getFirst();
    }

    private Dialog require(String dialogId) {
        Dialog dialog = store.load(dialogId);
        if (dialog == null) {
            throw new DialogNotFoundException(dialogId);
        }
        return dialog;
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
        builder.append("Рабочую память используй как актуальные данные текущей задачи, ")
                .append("долговременную — как профиль и накопленные знания.");
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

    private static String overflowMessage(long promptTokens, long limit) {
        return "Запрос превысил контекстное окно модели: " + promptTokens + " токенов при лимите " + limit
                + ". Вопрос не отправлен в модель. Завершите диалог и начните новый.";
    }
}