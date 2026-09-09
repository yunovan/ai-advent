package com.yunovan.aiadvent.day08;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenEstimatorTest {

    private final TokenEstimator estimator = new TokenEstimator();

    @Test
    void blankTextHasZeroTokens() {
        assertThat(estimator.estimate((String) null)).isZero();
        assertThat(estimator.estimate("")).isZero();
        assertThat(estimator.estimate("   ")).isZero();
    }

    @Test
    void nonBlankTextHasAtLeastOneToken() {
        assertThat(estimator.estimate("a")).isEqualTo(1L);
        assertThat(estimator.estimate("Привет")).isGreaterThan(0L);
    }

    @Test
    void asciiTextRoughlyFourCharsPerToken() {
        long tokens = estimator.estimate("Hello world, this is a fairly long English sentence");

        assertThat(tokens).isBetween(8L, 14L);
    }

    @Test
    void cyrillicTextCostsMoreTokensPerCharThanExpected() {
        long ascii = estimator.estimate("Hello, my name is Asya");
        long cyrillic = estimator.estimate("Привет, меня зовут Ася");

        assertThat(cyrillic).isGreaterThan(ascii);
    }

    @Test
    void longerTextHasMoreTokens() {
        long shortText = estimator.estimate("короткое сообщение");
        long longText = estimator.estimate("это гораздо более длинное сообщение, которое занимает заметно больше токенов");

        assertThat(longText).isGreaterThan(shortText);
    }

    @Test
    void estimateSummesOverMessages() {
        long total = estimator.estimate(List.of(
                ConversationMessage.user("Привет"),
                ConversationMessage.assistant("Здравствуйте!")));

        assertThat(total).isGreaterThan(estimator.estimate("Привет"));
    }
}