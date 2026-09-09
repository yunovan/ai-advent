package com.yunovan.aiadvent.day07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.agent.dialog.DialogStore;
import com.yunovan.aiadvent.agent.dialog.DialogSummarizer;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Day07DialogServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private DialogStore store;

    @Mock
    private DialogSummarizer summarizer;

    private Day07DialogService service() {
        return new Day07DialogService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                store,
                new DialogContext(),
                summarizer);
    }

    @Test
    void startCreatesDialog() {
        Dialog dialog = Dialog.create();
        when(store.create()).thenReturn(dialog);
        when(store.finishedDialogs()).thenReturn(List.of());

        Day07StartResponse response = service().start();

        assertThat(response.dialogId()).isEqualTo(dialog.id());
        assertThat(response.history()).isEmpty();
        assertThat(response.memory()).isEmpty();
    }

    @Test
    void chatIncludesMemoryOfFinishedDialogsInSystemPrompt() {
        Dialog current = Dialog.create();
        when(store.load(current.id())).thenReturn(current);
        Dialog past = new Dialog(
                "past",
                Instant.now(),
                Instant.now(),
                "Говорили о том, почему небо синее.",
                List.of());
        when(store.finishedDialogs()).thenReturn(List.of(past));
        when(llmClient.complete(any(CompletionCommand.class), any()))
                .thenReturn(new LlmReply("Вы раньше говорили про небо.", "stop", 5, 7, 12, BigDecimal.ZERO, 100L));

        Day07ChatResponse response = service().chat(current.id(), "О чём мы говорили раньше?");

        assertThat(response.content()).isEqualTo("Вы раньше говорили про небо.");
        assertThat(response.messageCount()).isEqualTo(2);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(any(CompletionCommand.class), messagesCaptor.capture());
        List<ChatCompletionRequest.Message> llm = messagesCaptor.getValue();
        assertThat(llm.getFirst().role()).isEqualTo("system");
        assertThat(llm.getFirst().content()).contains("Говорили о том, почему небо синее.");
        assertThat(llm.get(1).content()).isEqualTo("О чём мы говорили раньше?");

        ArgumentCaptor<Dialog> savedCaptor = ArgumentCaptor.forClass(Dialog.class);
        verify(store).save(savedCaptor.capture());
        assertThat(savedCaptor.getValue().messages()).extracting(ConversationMessage::role)
                .containsExactly("user", "assistant");
    }

    @Test
    void chatRejectsBlankRequest() {
        assertThatThrownBy(() -> service().chat("some-id", "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void chatOnFinishedDialogIsRejected() {
        Dialog finished = Dialog.create().finished("итог", Instant.now());
        when(store.load(finished.id())).thenReturn(finished);

        assertThatThrownBy(() -> service().chat(finished.id(), "вопрос"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("завершён");
    }

    @Test
    void chatOnUnknownDialogThrowsNotFound() {
        when(store.load("nope")).thenReturn(null);

        assertThatThrownBy(() -> service().chat("nope", "вопрос"))
                .isInstanceOf(DialogNotFoundException.class);
    }

    @Test
    void finishSummarizesAndMarksDialogFinished() {
        Dialog dialog = Dialog.create();
        when(store.load(dialog.id())).thenReturn(dialog);
        when(summarizer.summarize(dialog)).thenReturn("Итог: говорили про небо.");

        Day07FinishResponse response = service().finish(dialog.id());

        assertThat(response.summary()).isEqualTo("Итог: говорили про небо.");
        assertThat(response.finishedAt()).isNotNull();
        verify(store).save(org.mockito.ArgumentMatchers.argThat(saved ->
                saved.isFinished() && saved.summary().equals("Итог: говорили про небо.")));
    }

    @Test
    void finishIsIdempotent() {
        Dialog finished = Dialog.create().finished("старый итог", Instant.parse("2026-09-08T10:00:00Z"));
        when(store.load(finished.id())).thenReturn(finished);

        Day07FinishResponse response = service().finish(finished.id());

        assertThat(response.summary()).isEqualTo("старый итог");
        verify(summarizer, never()).summarize(any());
        verify(store, never()).save(any());
    }

    @Test
    void dialogsReturnsFinishedDialogs() {
        Dialog past = Dialog.create().finished("итог", Instant.now());
        when(store.finishedDialogs()).thenReturn(List.of(past));

        List<Day07DialogSummary> dialogs = service().dialogs();

        assertThat(dialogs).hasSize(1);
        assertThat(dialogs.getFirst().summary()).isEqualTo("итог");
    }
}