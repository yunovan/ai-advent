package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day21PdfRoundTripTest {

    @Test
    void generatedPdfExtractsBackAllSentences() {
        List<String> lines = List.of(
                "AI Advent - Day 21 indexation guide",
                "This tiny PDF is generated at runtime to prove that",
                "documents in PDF format can be converted to plain text",
                "and included into the local index without external tools.");

        byte[] pdf = Day21PdfWriter.generate(lines);
        String extracted = Day21PdfText.extract(pdf);

        assertThat(extracted)
                .contains("indexation guide")
                .contains("generated at runtime")
                .doesNotContain("(");
    }

    @Test
    void generatorWritesParenthesizedTextWithEscapes() {
        byte[] pdf = Day21PdfWriter.generate(List.of("Text (in parens) and \\ backslash"));
        String extracted = Day21PdfText.extract(pdf);

        assertThat(extracted).contains("(in parens)").contains("\\ backslash");
    }
}