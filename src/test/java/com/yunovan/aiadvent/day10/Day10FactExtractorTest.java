package com.yunovan.aiadvent.day10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.List;
import org.junit.jupiter.api.Test;

class Day10FactExtractorTest {

    private static final LlmProperties WITH_KEY =
            new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini");
    private static final LlmProperties WITHOUT_KEY = new LlmProperties(null, "https://openrouter.ai/api/v1", "gpt-4o-mini");

    @Test
    void parsesLocalFactsWhenNoApiKey() {
        LlmClient llmClient = mock(LlmClient.class);
        Day10FactExtractor extractor = new Day10FactExtractor(llmClient, WITHOUT_KEY);

        List<Day10Fact> facts = extractor.extract(
                "Цель: собрать ТЗ\nОграничение: бюджет 10 000$\nСкорость работы не важна");

        assertThat(facts).extracting(Day10Fact::key).containsExactly("Цель", "Ограничение");
        assertThat(facts).extracting(Day10Fact::value).contains("собрать ТЗ", "бюджет 10 000$");
        assertThat(facts).allMatch(Day10Fact::active);
        verify(llmClient, never()).complete(any(CompletionCommand.class));
    }

    @Test
    void returnsEmptyForBlankMessage() {
        Day10FactExtractor extractor = new Day10FactExtractor(mock(LlmClient.class), WITH_KEY);

        assertThat(extractor.extract("   ")).isEmpty();
    }

    @Test
    void usesLlmWhenApiKeyPresent() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Цель: собрать ТЗ\nПредпочтение: тёмная тема", "stop"));
        Day10FactExtractor extractor = new Day10FactExtractor(llmClient, WITH_KEY);

        List<Day10Fact> facts = extractor.extract("Давай сделаем ТЗ, предпочитаю тёмную тему");

        assertThat(facts).extracting(Day10Fact::key).containsExactly("Цель", "Предпочтение");
        assertThat(facts).extracting(Day10Fact::value).contains("собрать ТЗ", "тёмная тема");
    }

    @Test
    void fallsBackToLocalWhenLlmFails() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("context_length_exceeded"));
        Day10FactExtractor extractor = new Day10FactExtractor(llmClient, WITH_KEY);

        List<Day10Fact> facts = extractor.extract("Цель: собрать ТЗ прекрасно");

        assertThat(facts).extracting(Day10Fact::key).containsExactly("Цель");
    }

    @Test
    void ignoresLinesWithoutColon() {
        assertThat(extractLocal("это просто текст без двоеточия")).isEmpty();
    }

    private static List<Day10Fact> extractLocal(String text) {
        LlmClient llmClient = mock(LlmClient.class);
        Day10FactExtractor extractor = new Day10FactExtractor(llmClient, WITHOUT_KEY);
        return extractor.extract(text);
    }
}