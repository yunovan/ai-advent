package com.yunovan.aiadvent.day08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.store.FileConversationStore;
import com.yunovan.aiadvent.day07.Day7Properties;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class Day08TokenServiceTest {

    @TempDir
    Path tempDir;

    private static final BigDecimal PRICE = new BigDecimal("0.15");

    private final LlmClient llmClient = mock(LlmClient.class);
    private final TokenEstimator estimator = new TokenEstimator();

    private Day08TokenService newService(long contextLimit) {
        FileConversationStore store =
                new FileConversationStore(JsonMapper.builder().build(), new Day7Properties(tempDir.toString()));
        return new Day08TokenService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day8Properties(contextLimit, PRICE, new BigDecimal("0.60")),
                store,
                estimator);
    }

    @Test
    void chatCountsRequestHistoryAndResponseTokens() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Привет, Ася!"));
        Day08TokenService service = newService(128_000L);
        String request = "Привет, меня зовут Ася";

        Day08ChatResponse response = service.chat("default", request, null);

        assertThat(response.exceeded()).isFalse();
        assertThat(response.requestTokens()).isEqualTo(estimator.estimate(request));
        assertThat(response.historyTokens()).isZero();
        assertThat(response.promptTokens())
                .isEqualTo(estimator.estimate(List.of(
                        ConversationMessage.system(Day08TokenService.SYSTEM_PROMPT),
                        ConversationMessage.user(request))));
        assertThat(response.responseTokens()).isEqualTo(estimator.estimate("Привет, Ася!"));
        assertThat(response.realTotalTokens()).isEqualTo(17);
        assertThat(response.messageCount()).isEqualTo(3);
        assertThat(response.estimatedTurnCostUsd()).isNotNull();
    }

    @Test
    void historyTokensAndCumulativeCostGrowAcrossAsks() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Ответ без истории"));
        Day08TokenService service = newService(128_000L);

        service.chat("default", "Первый вопрос, довольно длинный и подробный", null);
        Day08ChatResponse second = service.chat("default", "Второй вопрос, ещё более длинный", null);
        Day08ChatResponse third = service.chat("default", "Третий", null);

        assertThat(second.historyTokens()).isPositive();
        assertThat(third.historyTokens()).isGreaterThan(second.historyTokens());
        assertThat(third.promptTokens()).isGreaterThan(second.promptTokens());
        assertThat(third.estimatedCumulativeCostUsd())
                .isGreaterThan(second.estimatedCumulativeCostUsd());
    }

    @Test
    void longDialogExceedingLimitIsBlockedBeforeCallingLlm() {
        Day08TokenService service = newService(60L);

        Day08ChatResponse exceeded =
                service.chat("default", "Очень длинное сообщение, которое превысит крошечный лимит модели", null);

        assertThat(exceeded.exceeded()).isTrue();
        assertThat(exceeded.promptTokens()).isGreaterThan(60L);
        assertThat(exceeded.content()).contains("превысил контекстное окно");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void dialogGrowsUntilItHitsTheLimitThenStopsCallingLlm() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Короткий ответ"));
        Day08TokenService service = newService(140L);

        Day08ChatResponse first = service.chat("default", "Привет, как дела?", null);
        Day08ChatResponse second = service.chat("default", "Расскажи подробнее про токены", null);
        Day08ChatResponse blocked = service.chat(
                "default",
                "Это очень длинное сообщение, которое надо написать максимально пространно, чтобы гарантированно "
                        + "переполнить контекстное окно, заданное в этом тесте",
                null);

        assertThat(first.exceeded()).isFalse();
        assertThat(second.exceeded()).isFalse();
        assertThat(blocked.exceeded()).isTrue();
        assertThat(blocked.promptTokens()).isGreaterThan(140L);
        assertThat(blocked.history()).hasSize(5);
        verify(llmClient, times(2)).complete(any(CompletionCommand.class), any());
    }

    @Test
    void metricsReportShowsGrowingTurnsAndCumulativeCost() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Короткий ответ здесь"));
        Day08TokenService service = newService(128_000L);

        service.chat("default", "Один вопрос, не очень длинный", null);
        service.chat("default", "Второй вопрос, чуть длиннее первого", null);

        Day08GrowthReport report = service.metrics("default");

        assertThat(report.turns()).hasSize(2);
        Day08GrowthTurn first = report.turns().get(0);
        Day08GrowthTurn second = report.turns().get(1);
        assertThat(second.promptTokens()).isGreaterThan(first.promptTokens());
        assertThat(second.cumulativeTokens()).isGreaterThan(first.cumulativeTokens());
        assertThat(second.cumulativeCostUsd()).isGreaterThan(first.cumulativeCostUsd());
        assertThat(report.totalTokens()).isPositive();
    }

    @Test
    void resetClearsSession() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Привет"));
        Day08TokenService service = newService(128_000L);
        service.chat("default", "Привет", null);

        service.reset("default");

        assertThat(service.metrics("default").turns()).isEmpty();
    }

    @Test
    void chatRejectsBlankRequest() {
        assertThatThrownBy(() -> newService(128_000L).chat("default", "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static LlmReply reply(String content) {
        return new LlmReply(content, "stop", 10, 7, 17, new BigDecimal("0.00001"), 100L);
    }
}