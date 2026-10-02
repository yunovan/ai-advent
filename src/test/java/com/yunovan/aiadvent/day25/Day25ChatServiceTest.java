package com.yunovan.aiadvent.day25;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21HealthResponse;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day21.Day21SearchResponse;
import com.yunovan.aiadvent.day24.Day24Source;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day25ChatServiceTest {

    private final Day21IndexFacade facade = mock(Day21IndexFacade.class);
    private final LlmClient llm = mock(LlmClient.class);

    @TempDir
    Path tempDir;

    private Day25ChatService service() {
        return service(null);
    }

    private Day25ChatService service(Day25Properties properties) {
        Day25Properties props = properties == null
                ? new Day25Properties(null, null, null, null, null, null, null, null, null,
                null, null, null, null, tempDir.toString())
                : properties;
        Day25ChatSessionStore store = new Day25ChatSessionStore(tempDir, props.maxSessions());
        return new Day25ChatService(facade, props, llm, store);
    }

    private Day21Chunk embeddingsChunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 120, "Эмбеддинги — это векторы числовых признаков. "
                + "Чем больше признаков в основе, тем точнее вектор для поиска.");
    }

    private Day21Chunk memoryChunk() {
        return new Day21Chunk("fixed", "articles/memory.md", "memory.md",
                "Память", "Долговременная память", "articles/memory.md#fixed#0002",
                0, 120, "Память агента хранит прошлые сообщения и цели диалога между сессиями.");
    }

    private Day21Chunk borschChunk() {
        return new Day21Chunk("fixed", "articles/random.md", "random.md",
                "Прочее", "Случайное", "articles/random.md#fixed#0003",
                0, 60, "Рецепт борща из свёклы режет морковь кубиками.");
    }

    private void stubKnown() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse("fixed",
                "эмбеддинги", 10,
                List.of(new Day21SearchHit(embeddingsChunk(), 0.85, "эмбеддинги и векторы"),
                        new Day21SearchHit(memoryChunk(), 0.55, "память"))));
        when(facade.chunks(any())).thenReturn(List.of(embeddingsChunk(), memoryChunk()));
    }

    private void stubWeak() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse("fixed",
                "погода", 10, List.of(new Day21SearchHit(borschChunk(), 0.9, "борщ"))));
        when(facade.chunks(any())).thenReturn(List.of(borschChunk()));
    }

    private void stubHealth() {
        when(facade.health()).thenReturn(new Day21HealthResponse("test", "0.0.1", 10, 4000, 2,
                List.of(new com.yunovan.aiadvent.day21.Day21StrategyInfo("fixed",
                        "Фиксированный чанкинг", true, 12))));
    }

    @Test
    void chatStoresHistoryAndReturnsSources() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatService service = service();
        Day25ChatTurn first = service.chat("s1", "Что такое эмбеддинги документов?");
        Day25ChatTurn second = service.chat("s1", "А как их считают?");

        assertThat(first.turn()).isEqualTo(1);
        assertThat(first.sources()).isNotEmpty();
        assertThat(second.turn()).isEqualTo(2);
        assertThat(second.historySize()).isEqualTo(4);
        assertThat(second.history().get(0).role()).isEqualTo("user");
        assertThat(second.history().get(1).role()).isEqualTo("assistant");
    }

    @Test
    void sourcesAlwaysCarrySourceSectionAndChunkId() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatTurn turn = service().chat("s2", "Что такое эмбеддинги документов?");

        assertThat(turn.sources()).isNotEmpty();
        for (Day24Source source : turn.sources()) {
            assertThat(source.source()).isNotBlank();
            assertThat(source.section()).isNotBlank();
            assertThat(source.chunkId()).isNotBlank();
            assertThat(source.score()).isPositive();
        }
    }

    @Test
    void goalIsRememberedAndUsedInSearch() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatService service = service();
        service.chat("s3", "Моя цель: разобраться в эмбеддингах документов.");
        Day25ChatTurn turn = service.chat("s3", "А как их считают?");

        assertThat(turn.memory().goal()).contains("эмбеддингах");
        assertThat(turn.matchedQuery()).containsIgnoringCase("эмбеддинг");
        assertThat(turn.historySize()).isEqualTo(4);
    }

    @Test
    void constraintsAreStoredAndKeptAcrossTurns() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatService service = service();
        service.chat("s4", "Только по документации, без выдумок.");
        Day25ChatTurn turn = service.chat("s4", "Что такое эмбеддинги?");

        assertThat(turn.memory().constraints()).isNotEmpty();
        assertThat(turn.memory().constraints().get(0)).contains("без выдумок");
    }

    @Test
    void clarificationsAreStoredForFollowUps() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatService service = service();
        service.chat("s5", "Моя цель: понять память агента.");
        Day25ChatTurn turn = service.chat("s5", "Имею в виду только долговременную память.");

        assertThat(turn.memory().clarifications()).isNotEmpty();
        assertThat(turn.memory().clarifications().get(0)).contains("долговременную");
        assertThat(turn.memory().terms()).contains("долговременную");
    }

    @Test
    void memoryIsIsolatedPerSession() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatService service = service();
        service.chat("a", "Моя цель: разобраться в эмбеддингах.");
        Day25ChatTurn other = service.chat("b", "Что такое эмбеддинги?");

        assertThat(other.memory().goal()).isNull();
        assertThat(service.memory("a").goal()).contains("эмбеддингах");
        assertThat(service.memory("b").goal()).isNull();
    }

    @Test
    void unknownQuestionSaysDontKnowWithoutInvokingLlm() {
        stubWeak();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatTurn turn = service().chat("s6", "Какая сегодня погода в Москве?");

        assertThat(turn.unknown()).isTrue();
        assertThat(turn.reply()).containsIgnoringCase("не знаю");
        assertThat(turn.sources()).isEmpty();
        assertThat(turn.quotes()).isEmpty();
    }

    @Test
    void blankMessageIsRejected() {
        assertThatThrownBy(() -> service().chat("s7", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void llmAnswerIsReturnedWithSources() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenReturn(
                new LlmReply("Эмбеддинги — это векторы числовых признаков документов.", "stop"));

        Day25ChatTurn turn = service().chat("s8", "Что такое эмбеддинги документов?");

        assertThat(turn.reply()).contains("Эмбеддинги");
        assertThat(turn.fallback()).isFalse();
        assertThat(turn.supported()).isTrue();
        assertThat(turn.sources()).isNotEmpty();
    }

    @Test
    void resetClearsSessionHistoryAndMemory() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatService service = service();
        service.chat("s9", "Моя цель: разобраться в эмбеддингах.");
        service.reset("s9");

        assertThat(service.history("s9")).isEmpty();
        assertThat(service.memory("s9").goal()).isNull();
        assertThat(service.memory("s9").turns()).isZero();
    }

    @Test
    void historyLimitTrimsOldestMessages() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));
        Day25ChatService service = service(new Day25Properties(null, null, null, null, null, null,
                null, null, null, null, 6, null, null, tempDir.toString()));

        for (int i = 0; i < 5; i++) {
            service.chat("s10", "Что такое эмбеддинги документов " + i + "?");
        }

        assertThat(service.history("s10")).hasSize(6);
        assertThat(service.memory("s10").turns()).isEqualTo(3);
    }

    @Test
    void healthReportsCorpusAndMemorySettings() {
        stubHealth();

        Day25HealthResponse health = service().health();

        assertThat(health.documents()).isEqualTo(10);
        assertThat(health.historyLimit()).isEqualTo(40);
        assertThat(health.memoryTermsLimit()).isEqualTo(8);
        assertThat(health.scenarios()).hasSize(2);
    }

    @Test
    void sessionsListShowsGoalAndHistory() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day25ChatService service = service();
        service.chat("s11", "Моя цель: разобраться в эмбеддингах.");
        List<Day25SessionView> sessions = service.sessions();

        assertThat(sessions).isNotEmpty();
        Day25SessionView view = sessions.stream()
                .filter(s -> s.sessionId().equals("s11")).findFirst().orElseThrow();
        assertThat(view.goal()).contains("эмбеддингах");
        assertThat(view.turns()).isEqualTo(1);
    }
}
