package com.yunovan.aiadvent.day12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class Day12DialogServiceTest {

    @TempDir
    Path tempDir;

    private static final BigDecimal PRICE = new BigDecimal("0.15");
    private static final BigDecimal OUT_PRICE = new BigDecimal("0.60");

    private final LlmClient llmClient = mock(LlmClient.class);
    private final DialogSummarizer summarizer = mock(DialogSummarizer.class);
    private final TokenEstimator estimator = new TokenEstimator();

    private Day12DialogStore dialogStore;
    private Day11FileMemoryStore memoryStore;
    private Day12ProfileStore profileStore;
    private Day12DialogProfileStore linkStore;
    private Day12DialogService service;
    private String dialogId;

    @BeforeEach
    void setUp() throws Exception {
        when(summarizer.summarize(any())).thenReturn("Итоговое саммари");
        dialogStore = new Day12DialogStore(tempDir.resolve("dialogs"));
        memoryStore = new Day11FileMemoryStore(tempDir.resolve("memory"));
        Path profileDir = tempDir.resolve("profiles");
        profileStore = new Day12ProfileStore(profileDir);
        linkStore = new Day12DialogProfileStore(profileDir.resolve("links"));
    }

    private void setup(int window, long limit) {
        service = new Day12DialogService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day12Properties(limit, PRICE, OUT_PRICE,
                        "data/day12-dialogs", "data/day12-profiles", "data/day12-memory", window),
                dialogStore,
                new DialogContext(),
                summarizer,
                profileStore,
                linkStore,
                memoryStore,
                estimator);
        dialogId = service.start(null).dialogId();
    }

    private LlmReply answer() {
        return new LlmReply("Ответ агента", "stop", 10, 7, 17, new BigDecimal("0.00001"), 100L);
    }

    private String systemPromptOfLastCall() {
        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).complete(any(CompletionCommand.class), captor.capture());
        return captor.getAllValues().getLast().getFirst().content();
    }

    @Test
    void startAssignsDefaultProfileAndListProfiles() {
        setup(10, 128_000L);
        Day12StartResponse response = service.start(null);

        assertThat(response.profile()).isNotNull();
        assertThat(response.profile().name()).isEqualTo("Ася");
        assertThat(response.profiles()).extracting(Day12Profile::name)
                .containsExactly("Ася", "Менеджер", "Разработчик");
        assertThat(linkStore.profileIdFor(response.dialogId())).isEqualTo(response.profile().id());
    }

    @Test
    void startWithNamedProfileAttachesIt() {
        setup(10, 128_000L);
        Day12StartResponse response = service.start("Менеджер");

        assertThat(response.profile().id()).isEqualTo("manager");
        assertThat(linkStore.profileIdFor(response.dialogId())).isEqualTo("manager");
    }

    @Test
    void startWithUnknownProfileFallsBackToDefault() {
        setup(10, 128_000L);
        Day12StartResponse response = service.start("нет-такого");

        assertThat(response.profile().name()).isEqualTo("Ася");
    }

    @Test
    void chatInjectsProfileBlockIntoSystemPrompt() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.chat(dialogId, "Привет", null);

        String system = systemPromptOfLastCall();
        assertThat(system).contains("Профиль пользователя:");
        assertThat(system).contains("- Имя: Ася");
        assertThat(system).contains("Учитывай этот профиль в каждом ответе");
    }

    @Test
    void differentProfilesProduceDifferentSystemPrompts() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.chat(dialogId, "Вопрос", null);
        String asyaBlock = systemPromptOfLastCall();

        String secondDialog = service.start("Менеджер").dialogId();
        service.chat(secondDialog, "Вопрос", null);
        String managerBlock = systemPromptOfLastCall();

        assertThat(asyaBlock).contains("- Имя: Ася");
        assertThat(managerBlock).contains("- Имя: Менеджер");
        assertThat(asyaBlock).doesNotContain("- Имя: Менеджер");
        assertThat(asyaBlock).isNotEqualTo(managerBlock);
    }

    @Test
    void profileIsAttachedToEveryRequestEvenAfterSwitch() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.setProfile(dialogId, "dev");

        service.chat(dialogId, "Первый", null);
        service.chat(dialogId, "Второй", null);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(2)).complete(any(CompletionCommand.class), captor.capture());
        assertThat(captor.getAllValues().getFirst().getFirst().content()).contains("Имя: Разработчик");
        assertThat(captor.getAllValues().getLast().getFirst().content()).contains("Имя: Разработчик");
    }

    @Test
    void workingAndLongTermMemoryAreInjectedAlongsideProfile() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.remember(dialogId, "Бюджет", "10 000$", "working");
        service.remember(dialogId, "Имя", "Ася", "long-term");

        service.chat(dialogId, "Что дальше?", null);

        String system = systemPromptOfLastCall();
        assertThat(system).contains("Профиль пользователя:");
        assertThat(system).contains("- Имя: Ася");
        assertThat(system).contains("Бюджет: 10 000$");
        assertThat(system).contains("- Имя: Ася");
    }

    @Test
    void longTermMemoryPersistsAcrossDialogsWithProfile() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.remember(dialogId, "Формат", "PDF", "long-term");

        String secondDialog = service.start("dev").dialogId();
        service.chat(secondDialog, "Как меня зовут?", null);

        String system = systemPromptOfLastCall();
        assertThat(system).contains("Имя: Разработчик");
        assertThat(system).contains("Формат: PDF");
    }

    @Test
    void setProfileByUnknownIdFails() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.setProfile(dialogId, "нет-такого"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    void chatBeyondLimitReturnsExceededWithoutCallingModel() {
        setup(10, 60L);

        Day12ChatResponse response = service.chat(
                dialogId, "Очень длинный вопрос много слов ".repeat(30), null);

        assertThat(response.exceeded()).isTrue();
        assertThat(response.content()).contains("контекстное окно");
        assertThat(response.profile()).isNotNull();
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void chatResponseCarriesProfileAndMemoryLayers() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.remember(dialogId, "Цель", "собрать ТЗ", "working");

        Day12ChatResponse response = service.chat(dialogId, "Соберём ТЗ", null);

        assertThat(response.profile().name()).isEqualTo("Ася");
        assertThat(response.working()).extracting(Day11MemoryEntry::key).contains("Цель");
        assertThat(response.longTerm()).isEmpty();
        assertThat(response.messageCount()).isEqualTo(2);
    }

    @Test
    void createProfileSavesNewProfileAndListsIt() {
        setup(10, 128_000L);

        Day12Profile created = service.createProfile(
                "Тестировщик", "аккуратно", "чек-листы", List.of("без абстракций"), "QA-инженер");

        assertThat(created.id()).isEqualTo("testirovshchik");
        assertThat(service.profiles()).extracting(Day12Profile::name).contains("Тестировщик");
    }

    @Test
    void createProfileRejectsBlankName() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.createProfile(" ", "", "", List.of(), ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void profileLookupByIdOrName() {
        setup(10, 128_000L);

        assertThat(service.profile("dev").name()).isEqualTo("Разработчик");
        assertThat(service.profile("Менеджер").name()).isEqualTo("Менеджер");
        assertThat(service.profile("нет-такого")).isNull();
    }

    @Test
    void rememberStoresEntryInLongTermMemory() {
        setup(10, 128_000L);

        service.remember(dialogId, "Стек", "Java 21", "long-term");

        assertThat(memoryStore.find(Day11MemoryLayer.LONG_TERM, "Стек").value()).isEqualTo("Java 21");
        assertThat(service.get(dialogId).longTerm()).hasSize(1);
    }

    @Test
    void rememberRejectsBlankKey() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.remember(dialogId, " ", "значение", "working"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.remember(dialogId, null, "значение", "working"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dialogsListsAllWithProfiles() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.chat(dialogId, "Вопрос", null);
        service.finish(dialogId);
        String second = service.start("dev").dialogId();

        assertThat(service.dialogs()).extracting(Day12DialogInfo::dialogId).contains(dialogId, second);
        assertThat(service.dialogs()).extracting(Day12DialogInfo::profile)
                .anyMatch(profile -> profile != null && profile.id().equals("dev"));
        verify(summarizer, atLeastOnce()).summarize(any());
    }

    @Test
    void finishReturnsProfileAndSeedsSummaryMemory() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.chat(dialogId, "Разговор для итога", null);

        Day12FinishResponse response = service.finish(dialogId);

        assertThat(response.dialogId()).isEqualTo(dialogId);
        assertThat(response.summary()).isEqualTo("Итоговое саммари");
        assertThat(response.profileId()).isEqualTo("asya");
        assertThat(dialogStore.load(dialogId).isFinished()).isTrue();
        assertThat(memoryStore.find(Day11MemoryLayer.LONG_TERM, "итог:" + dialogId)).isNotNull();
    }

    @Test
    void chatOnFinishedDialogIsRejected() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.chat(dialogId, "Вопрос", null);
        service.finish(dialogId);

        assertThatThrownBy(() -> service.chat(dialogId, "Ещё вопрос", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("завершён");
    }

    @Test
    void chatRejectsBlankRequest() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.chat(dialogId, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatOnUnknownDialogThrowsNotFound() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.chat("no-such-dialog", "Вопрос?", null))
                .isInstanceOf(DialogNotFoundException.class);
    }

    @Test
    void chatPreservesHistoryAndShortTermWindow() {
        setup(2, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        for (int i = 1; i <= 4; i++) {
            service.chat(dialogId, "Вопрос номер " + i, null);
        }

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(4)).complete(any(CompletionCommand.class), captor.capture());
        List<ChatCompletionRequest.Message> last = captor.getAllValues().getLast();
        assertThat(last).hasSize(4);
        assertThat(last).extracting(ChatCompletionRequest.Message::content)
                .doesNotContain("Вопрос номер 1", "Вопрос номер 2");
        assertThat(last.getLast().content()).isEqualTo("Вопрос номер 4");
        assertThat(last.getFirst().content()).contains("Профиль пользователя:");
    }

    @Test
    void chatReturnsTwoMessagesPerTurnInHistory() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        Day12ChatResponse response = service.chat(dialogId, "Привет", null);

        assertThat(response.history())
                .extracting(ConversationMessage::role)
                .containsExactly("user", "assistant");
    }
}