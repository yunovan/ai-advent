package com.yunovan.aiadvent.day08;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Локальная оценка числа токенов без вызова LLM.
 *
 * <p>Точную раскладку по токенам знает только токенизатор модели (BPE у OpenAI).
 * Здесь используется общепринятая эвристика по количеству символов:
 * ~4 латинских символа на токен и ~1.6 символа кириллицы/прочих алфавитов на токен.
 * Оценки детерминированы и подходят, чтобы наблюдать, как токены и цена растут по мере диалога.
 */
@Component
public class TokenEstimator {

    private static final double ASCII_TOKENS_PER_CHAR = 0.25;
    private static final double NON_ASCII_TOKENS_PER_CHAR = 0.6;

    public long estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }
        double tokens = 0.0;
        for (int i = 0; i < text.length(); i++) {
            tokens += text.charAt(i) < 128 ? ASCII_TOKENS_PER_CHAR : NON_ASCII_TOKENS_PER_CHAR;
        }
        return Math.max(1L, Math.round(tokens));
    }

    public long estimate(List<ConversationMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (ConversationMessage message : messages) {
            total += estimate(message.content());
        }
        return total;
    }
}