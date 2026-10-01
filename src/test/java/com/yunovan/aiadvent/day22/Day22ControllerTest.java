package com.yunovan.aiadvent.day22;

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

@WebMvcTest(controllers = {Day22Controller.class, ApiExceptionHandler.class})
class Day22ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day22RagService service;

    private Day21SearchHit hit() {
        return new Day21SearchHit(
                new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                        "Эмбеддинги", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001", 0, 100, "текст про эмбеддинги"),
                0.9, "сниппет");
    }

    @Test
    void getHealthReturnsStatus() throws Exception {
        when(service.health()).thenReturn(new Day22HealthResponse(9, 131000, 44, "fixed", 3, 400,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));

        mockMvc.perform(get("/api/day22/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(9))
                .andExpect(jsonPath("$.pagesEstimate").value(44))
                .andExpect(jsonPath("$.strategy").value("fixed"))
                .andExpect(jsonPath("$.topK").value(3))
                .andExpect(jsonPath("$.strategies[0].indexed").value(true));
    }

    @Test
    void getQuestionsReturnsTenControlQuestions() throws Exception {
        when(service.questions()).thenReturn(Day22ControlQuestions.ALL);

        mockMvc.perform(get("/api/day22/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].id").value("q01"))
                .andExpect(jsonPath("$[0].expectedSources[0]").value("embeddings.md"));
    }

    @Test
    void postAnswerReturnsAnswerInChosenMode() throws Exception {
        when(service.answer(eq("Что такое эмбеддинги?"), eq("rag"))).thenReturn(
                new Day22AnswerResponse("Что такое эмбеддинги?", "rag",
                        List.of(hit()), "Эмбеддинги — это векторы.", false));

        mockMvc.perform(post("/api/day22/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\",\"mode\":\"rag\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("rag"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.retrievedHits[0].chunk.fileName").value("embeddings.md"))
                .andExpect(jsonPath("$.answer").value("Эмбеддинги — это векторы."));
    }

    @Test
    void postAskAutoDetectsMode() throws Exception {
        when(service.ask(eq("Ответь без контекста про эмбеддинги"))).thenReturn(
                new Day22AnswerResponse("Ответь без контекста про эмбеддинги", "plain",
                        List.of(), "Из своей памяти", true));

        mockMvc.perform(post("/api/day22/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Ответь без контекста про эмбеддинги\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("plain"))
                .andExpect(jsonPath("$.fallback").value(true));
    }

    @Test
    void postCompareReturnsBothModes() throws Exception {
        when(service.compare(eq("Что такое эмбеддинги?"))).thenReturn(new Day22CompareResponse(
                "Что такое эмбеддинги?",
                new Day22AnswerResponse("Что такое эмбеддинги?", "rag", List.of(hit()),
                        "Ответ с контекстом", false),
                new Day22AnswerResponse("Что такое эмбеддинги?", "plain", List.of(),
                        "Ответ без контекста", false),
                List.of("embeddings.md"), "С RAG модель получила контекст."));

        mockMvc.perform(post("/api/day22/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rag.mode").value("rag"))
                .andExpect(jsonPath("$.plain.mode").value("plain"))
                .andExpect(jsonPath("$.retrievedSources[0]").value("embeddings.md"))
                .andExpect(jsonPath("$.verdict").value("С RAG модель получила контекст."));
    }

    @Test
    void postEvaluateReturnsQualityReport() throws Exception {
        when(service.evaluate()).thenReturn(new Day22EvalResponse(10, 8, 80.0, 70.0, 5.0, 65.0,
                "RAG даёт точнее", List.of(
                        new Day22EvalItem("q01", "Что такое эмбеддинги?", List.of("эмбеддинг"),
                                List.of("embeddings.md"), List.of("embeddings.md"), true,
                                100.0, 0.0, 100.0))));

        mockMvc.perform(post("/api/day22/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.retrievalRecallPercent").value(80.0))
                .andExpect(jsonPath("$.items[0].retrievalHit").value(true));
    }

    @Test
    void postAnswerMapsBlankQuestionToBadRequest() throws Exception {
        when(service.answer(any(), any()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day22/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }

    @Test
    void postEvaluateMapsIndexFailureToServerError() throws Exception {
        when(service.evaluate())
                .thenThrow(new Day21IndexException("Не удалось прочитать индекс"));

        mockMvc.perform(post("/api/day22/evaluate"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Не удалось прочитать индекс"));
    }
}