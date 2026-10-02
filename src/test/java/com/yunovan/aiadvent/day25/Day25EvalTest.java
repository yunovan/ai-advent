package com.yunovan.aiadvent.day25;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day21.Day21CorpusLoader;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21FixedChunker;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21IndexStore;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21StructuralChunker;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import java.nio.file.Path;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day25EvalTest {

    @TempDir
    Path tempDir;

    private Day25ChatService service() {
        Day21Properties properties = new Day21Properties(0, "/mcp", "test", "0.0.1",
                tempDir.resolve("store").toString(), tempDir.resolve("corpus").toString(),
                600, 80, 512);
        Day21IndexFacade facade = new Day21IndexFacade(
                new Day21CorpusLoader(properties),
                new Day21EmbeddingService(properties),
                new Day21IndexStore(properties), properties,
                List.of(new Day21FixedChunker(properties), new Day21StructuralChunker(properties)));
        LlmClient llm = mock(LlmClient.class);
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));
        Day25Properties day25 = new Day25Properties(null, null, null, null, null, null, null,
                null, null, null, null, null, null, tempDir.resolve("chat").toString());
        return new Day25ChatService(facade, day25, llm,
                new Day25ChatSessionStore(tempDir.resolve("chat"), day25.maxSessions()));
    }

    @Test
    void longScenariosKeepGoalAndAlwaysShowSources() {
        Day25EvalResponse evaluate = service().evaluate();

        assertThat(evaluate.scenariosCount()).isEqualTo(2);
        assertThat(evaluate.totalTurns()).isGreaterThanOrEqualTo(20);
        assertThat(evaluate.goalRetainedTurns()).isEqualTo(evaluate.totalTurns());
        assertThat(evaluate.turnsWithSources()).isEqualTo(evaluate.totalTurns());
        assertThat(evaluate.quotesTurns()).isEqualTo(evaluate.totalTurns());
        assertThat(evaluate.verdict()).contains("цель диалога удержана");
    }

    @Test
    void everyScenarioKeepsGoalAndConstraintsInMemory() {
        Day25EvalResponse evaluate = service().evaluate();

        for (Day25ScenarioResult result : evaluate.scenarios()) {
            assertThat(result.turns()).isGreaterThanOrEqualTo(10);
            assertThat(result.goalRetainedTurns()).isEqualTo(result.turns());
            assertThat(result.turnsWithSources()).isEqualTo(result.turns());
            assertThat(result.finalMemory().goal()).isNotBlank();
            assertThat(result.finalMemory().constraints()).isNotEmpty();
            assertThat(result.finalMemory().clarifications()).isNotEmpty();
            assertThat(result.finalMemory().terms()).isNotEmpty();
            assertThat(result.details()).allSatisfy(turn -> {
                assertThat(turn.goalRetained()).isTrue();
                assertThat(turn.hasSources()).isTrue();
            });
            // ограничение появляется на втором сообщении, до него оно ещё не задано
            assertThat(result.constraintsKeptTurns()).isEqualTo(result.turns() - 1);
            assertThat(result.details().subList(1, result.turns())).allSatisfy(turn ->
                    assertThat(turn.constraintsKept()).isTrue());
        }
    }
}
