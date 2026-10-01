package com.yunovan.aiadvent.day23;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21IndexException;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day21.Day21StrategyInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day23Controller.class, ApiExceptionHandler.class})
class Day23ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day23RagService service;

    private Day21SearchHit hit() {
        return new Day21SearchHit(
                new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                        "Эмбеддинги", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001", 0, 100, "текст про эмбеддинги"),
                0.9, "сниппет");
    }

    @Test
    void getHealthReturnsConfiguration() throws Exception {
        when(service.health()).thenReturn(new Day23HealthResponse(9, 131000, 44, "fixed",
                10, 5, 0.5, true, 400,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));

        mockMvc.perform(get("/api/day23/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(9))
                .andExpect(jsonPath("$.pagesEstimate").value(44))
                .andExpect(jsonPath("$.strategy").value("fixed"))
                .andExpect(jsonPath("$.topKBefore").value(10))
                .andExpect(jsonPath("$.topKAfter").value(5))
                .andExpect(jsonPath("$.threshold").value(0.5))
                .andExpect(jsonPath("$.rewrite").value(true))
                .andExpect(jsonPath("$.strategies[0].indexed").value(true));
    }

    @Test
    void getQuestionsReturnsTenControlQuestions() throws Exception {
        when(service.questions()).thenReturn(Day23ControlQuestions.ALL);

        mockMvc.perform(get("/api/day23/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].id").value("q01"))
                .andExpect(jsonPath("$[0].expectedSources[0]").value("embeddings.md"));
    }

    @Test
    void postRewriteReturnsExpandedQuery() throws Exception {
        when(service.rewrite(eq("Что такое эмбеддинги?"))).thenReturn(
                new Day23RewriteResponse("Что такое эмбеддинги?",
                        "Что такое эмбеддинги? вектор признак n-грамм документ",
                        true, List.of("эмбеддинг вектор признак n-грамм документ")));

        mockMvc.perform(post("/api/day23/rewrite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.rewritten").value(
                        "Что такое эмбеддинги? вектор признак n-грамм документ"))
                .andExpect(jsonPath("$.expansions[0]").value(
                        "эмбеддинг вектор признак n-грамм документ"));
    }

    @Test
    void postAnswerReturnsPipelineStatsAndAnswer() throws Exception {
        when(service.answer(eq("Что такое эмбеддинги?"), eq("full"))).thenReturn(
                new Day23AnswerResponse("Что такое эмбеддинги?", "full",
                        "Что такое эмбеддинги? вектор признак n-грамм документ", true,
                        10, 5, List.of(hit()), "Эмбеддинги — это векторы.", false));

        mockMvc.perform(post("/api/day23/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\",\"mode\":\"full\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("full"))
                .andExpect(jsonPath("$.rewritten").value(true))
                .andExpect(jsonPath("$.candidatesBefore").value(10))
                .andExpect(jsonPath("$.filteredOut").value(5))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.retrievedHits[0].chunk.fileName").value("embeddings.md"))
                .andExpect(jsonPath("$.answer").value("Эмбеддинги — это векторы."));
    }

    @Test
    void postAskAutoDetectsPipeline() throws Exception {
        when(service.ask(eq("Ответь без фильтра про эмбеддинги"))).thenReturn(
                new Day23AnswerResponse("Ответь без фильтра про эмбеддинги", "base",
                        "Ответь без фильтра про эмбеддинги", false,
                        10, 0, List.of(), "Из контекста", true));

        mockMvc.perform(post("/api/day23/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Ответь без фильтра про эмбеддинги\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("base"))
                .andExpect(jsonPath("$.fallback").value(true));
    }

    @Test
    void postCompareReturnsBothModesAndVerdict() throws Exception {
        when(service.compare(eq("Что такое эмбеддинги?"), any(), any())).thenReturn(
                new Day23CompareResponse("Что такое эмбеддинги?",
                        new Day23AnswerResponse("Что такое эмбеддинги?", "base",
                                "Что такое эмбеддинги?", false, 10, 0,
                                List.of(hit()), "Ответ без фильтра", false),
                        new Day23AnswerResponse("Что такое эмбеддинги?", "full",
                                "Что такое эмбеддинги?", false, 10, 7,
                                List.of(hit()), "Ответ с фильтром", false),
                        List.of("embeddings.md"), List.of("embeddings.md"),
                        "Фильтр отсеял нерелевантное."));

        mockMvc.perform(post("/api/day23/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.first.mode").value("base"))
                .andExpect(jsonPath("$.second.mode").value("full"))
                .andExpect(jsonPath("$.sourcesAfter[0]").value("embeddings.md"))
                .andExpect(jsonPath("$.verdict").value("Фильтр отсеял нерелевантное."));
    }

    @Test
    void postEvaluateReturnsQualityReport() throws Exception {
        when(service.evaluate()).thenReturn(new Day23EvalResponse(10, 9, 10, 90.0, 100.0, 23,
                60.0, 75.0, 15.0, 8, "Фильтр работает", List.of(
                        new Day23EvalItem("q01", "Что такое эмбеддинги?",
                                List.of("эмбеддинг"), List.of("embeddings.md"),
                                List.of("embeddings.md"), true, 100.0, 9,
                                List.of("embeddings.md"), true, 100.0, 2))));

        mockMvc.perform(post("/api/day23/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.baseRecallPercent").value(90.0))
                .andExpect(jsonPath("$.fullRecallPercent").value(100.0))
                .andExpect(jsonPath("$.totalFilteredOut").value(23))
                .andExpect(jsonPath("$.improved").value(8))
                .andExpect(jsonPath("$.items[0].fullHit").value(true));
    }

    @Test
    void postAnswerMapsBlankQuestionToBadRequest() throws Exception {
        when(service.answer(any(), any()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day23/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }

    @Test
    void postEvaluateMapsIndexFailureToServerError() throws Exception {
        when(service.evaluate())
                .thenThrow(new Day21IndexException("Не удалось прочитать индекс"));

        mockMvc.perform(post("/api/day23/evaluate"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Не удалось прочитать индекс"));
    }
}