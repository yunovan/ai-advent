package com.yunovan.aiadvent.day05;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class Day5ModelsTest {

    @Test
    void conclusionHighlightsFastestAndCheapest() {
        ModelRun weak = ModelRun.from(
                ModelTier.WEAK,
                Day5Models.WEAK_MODEL,
                new LlmReply("a", "stop", 5, 5, 10, new BigDecimal("0.00001"), 40L));
        ModelRun strong = ModelRun.from(
                ModelTier.STRONG,
                Day5Models.STRONG_MODEL,
                new LlmReply("bbbb", "stop", 5, 45, 50, new BigDecimal("0.002"), 800L));

        String text = Day5Models.conclusion(List.of(weak, strong));

        assertThat(text).contains("Быстрее всех").contains("3B");
        assertThat(text).contains("Дешевле");
        assertThat(text).contains("Качество");
    }

    @Test
    void formatCostHandlesMissingAndZero() {
        assertThat(Day5Models.formatCost(null)).isEqualTo("н/д");
        assertThat(Day5Models.formatCost(BigDecimal.ZERO)).isEqualTo("$0");
        assertThat(Day5Models.formatCost(new BigDecimal("0.00012"))).isEqualTo("$0.00012");
    }

    @Test
    void huggingFaceUrlUsesKnownCards() {
        assertThat(Day5Models.huggingFaceUrl(Day5Models.WEAK_MODEL)).contains("Llama-3.2-3B-Instruct");
        assertThat(Day5Models.openRouterUrl(Day5Models.STRONG_MODEL))
                .isEqualTo("https://openrouter.ai/" + Day5Models.STRONG_MODEL);
    }
}
