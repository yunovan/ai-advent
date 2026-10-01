package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Day21AgentServiceTest {

    private final Day21IndexFacade facade = mock(Day21IndexFacade.class);
    private final LlmClient llm = mock(LlmClient.class);

    private Day21AgentService service() {
        return new Day21AgentService(facade, llm);
    }

    private Day21Chunk chunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Текст про локальные эмбеддинги и хэш-признаки.");
    }

    @Test
    void searchPromptSelectsSearchIntentAndExtractsQuery() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                Day21Properties.STRATEGY_FIXED, "эмбеддинги", 3,
                List.of(new Day21SearchHit(chunk(), 0.85, "текст про эмбеддинги"))));
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day21AgentResponse response = service().submit("найди что такое эмбеддинги");

        assertThat(response.intent()).isEqualTo("search");
        assertThat(response.tool()).isEqualTo("index_search");
        assertThat(response.arguments()).containsEntry("query", "эмбеддинги");
        assertThat(response.arguments()).containsEntry("strategy", Day21Properties.STRATEGY_FIXED);
        assertThat(response.toolResult()).contains("Найдено по запросу").contains("embeddings.md");
        assertThat(response.answer()).contains("Результат инструмента");
    }

    @Test
    void structuralMentionSelectsStructuralStrategy() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                Day21Properties.STRATEGY_STRUCTURAL, "память", 3, List.of()));
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day21AgentResponse response = service().submit("найди в структурной нарезке про память");

        assertThat(response.arguments()).containsEntry("strategy", Day21Properties.STRATEGY_STRUCTURAL);
    }

    @Test
    void comparePromptSelectsCompareIntent() {
        when(facade.compare()).thenReturn(new Day21ComparisonResponse(
                List.of(), List.of(), "Лучше structural"));
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day21AgentResponse response = service().submit("какая стратегия чанкинга лучше");

        assertThat(response.intent()).isEqualTo("compare");
        assertThat(response.tool()).isEqualTo("index_compare");
        assertThat(response.toolResult()).contains("Сравнение стратегий");
    }

    @Test
    void ingestPromptSelectsIngestIntent() {
        when(facade.ingest(any())).thenReturn(new Day21IngestResponse(
                Day21Properties.STRATEGY_FIXED, 9, 100, 130000, "index-fixed.json"));
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day21AgentResponse response = service().submit("проиндексируй корпус документов");

        assertThat(response.intent()).isEqualTo("ingest");
        assertThat(response.tool()).isEqualTo("index_ingest");
        assertThat(response.toolResult()).contains("Индекс «fixed» построен");
    }

    @Test
    void llmSuccessIsUsedForAnswer() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                Day21Properties.STRATEGY_FIXED, "мсп", 3, List.of()));
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Ответ сформирован моделью", "stop"));

        Day21AgentResponse response = service().submit("найди про mcp");

        assertThat(response.answer()).isEqualTo("Ответ сформирован моделью");
    }

    @Test
    void blankPromptIsRejected() {
        assertThatThrownBy(() -> service().submit("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
    }

    @Test
    void unknownPromptHintsAtSupportedIntents() {
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        assertThatThrownBy(() -> service().submit("просто болтовня без маркеров"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("проиндексируй корпус");
    }
}