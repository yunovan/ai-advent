package com.yunovan.aiadvent.day11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.List;
import org.junit.jupiter.api.Test;

class Day11FactExtractorTest {

    private static final LlmProperties WITH_KEY =
            new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini");
    private static final LlmProperties WITHOUT_KEY =
            new LlmProperties(null, "https://openrouter.ai/api/v1", "gpt-4o-mini");

    @Test
    void parsesCandidateFactsLocallyWhenNoApiKey() {
        Day11FactExtractor extractor = new Day11FactExtractor(mock(LlmClient.class), WITHOUT_KEY);

        List<Day11MemoryEntry> entries = extractor.extract(
                "Цель: собрать ТЗ\nОграничение: бюджет 10 000$\nхочу тёмную тему");

        assertThat(entries).extracting(Day11MemoryEntry::key).containsExactly("Цель", "Ограничение");
        assertThat(entries).extracting(Day11MemoryEntry::value).contains("собрать ТЗ", "бюджет 10 000$");
        assertThat(entries).allMatch(Day11MemoryEntry::isCandidate);
        assertThat(entries).allMatch(entry -> entry.layer() == Day11MemoryLayer.SHORT_TERM);
    }

    @Test
    void returnsEmptyForBlankMessage() {
        Day11FactExtractor extractor = new Day11FactExtractor(mock(LlmClient.class), WITH_KEY);

        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    void usesLlmWhenApiKeyPresent() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Цель: собрать ТЗ\nИмя: Ася", "stop"));
        Day11FactExtractor extractor = new Day11FactExtractor(llmClient, WITH_KEY);

        List<Day11MemoryEntry> entries = extractor.extract("Соберём ТЗ, меня зовут Ася");

        assertThat(entries).extracting(Day11MemoryEntry::key).containsExactly("Цель", "Имя");
        assertThat(entries).extracting(Day11MemoryEntry::value).contains("собрать ТЗ", "Ася");
    }

    @Test
    void fallsBackToLocalWhenLlmFails() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("context_length_exceeded"));
        Day11FactExtractor extractor = new Day11FactExtractor(llmClient, WITH_KEY);

        List<Day11MemoryEntry> entries = extractor.extract("Цель: собрать ТЗ прекрасно");

        assertThat(entries).extracting(Day11MemoryEntry::key).containsExactly("Цель");
        verify(llmClient).complete(any(CompletionCommand.class));
    }

    @Test
    void ignoresLinesWithoutColon() {
        Day11FactExtractor extractor = new Day11FactExtractor(mock(LlmClient.class), WITHOUT_KEY);

        assertThat(extractor.extract("это просто текст без двоеточия")).isEmpty();
    }
}