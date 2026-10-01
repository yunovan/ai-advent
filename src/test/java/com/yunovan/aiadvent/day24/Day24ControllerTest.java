package com.yunovan.aiadvent.day24;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day21.Day21IndexException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day24Controller.class, ApiExceptionHandler.class})
class Day24ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day24Service service;

    private Day24GroundedResponse grounded() {
        return new Day24GroundedResponse("Что такое эмбеддинги документов?",
                "Что такое эмбеддинги? вектор признак n-грамм документ", true, 10, 2,
                List.of(new Day24Source("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001", 0.9, "сниппет")),
                List.of(new Day24Quote("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001",
                        "Эмбеддинги — это векторы, а n-граммы становятся признаками.",
                        0.9, 2)),
                "В документах говорится: «Эмбеддинги — это векторы…»", 0.9, 100.0,
                true, false, true);
    }

    @Test
    void getHealthReturnsConfiguration() throws Exception {
        when(service.health()).thenReturn(new Day24HealthResponse(9, 131000, 44, "fixed",
                10, 5, 0.5, 0.32, true, 3, 24, 0.35, 400,
                List.of("Загадай число от одного до ста"),
                List.of("fixed")));

        mockMvc.perform(get("/api/day24/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(9))
                .andExpect(jsonPath("$.unknownThreshold").value(0.32))
                .andExpect(jsonPath("$.quotesPerSource").value(3))
                .andExpect(jsonPath("$.quoteMinChars").value(24))
                .andExpect(jsonPath("$.supportThreshold").value(0.35))
                .andExpect(jsonPath("$.weakQuestions[0]").value("Загадай число от одного до ста"))
                .andExpect(jsonPath("$.strategies[0]").value("fixed"));
    }

    @Test
    void getQuestionsReturnsTenControlQuestions() throws Exception {
        when(service.questions()).thenReturn(Day24ControlQuestions.ALL);

        mockMvc.perform(get("/api/day24/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].id").value("q01"))
                .andExpect(jsonPath("$[0].expectedSources[0]").value("embeddings.md"));
    }

    @Test
    void postAnswerReturnsSourcesQuotesAndUnknownFlag() throws Exception {
        when(service.answer(eq("Что такое эмбеддинги документов?"))).thenReturn(grounded());

        mockMvc.perform(post("/api/day24/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги документов?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unknown").value(false))
                .andExpect(jsonPath("$.supported").value(true))
                .andExpect(jsonPath("$.sources[0].source").value("articles/embeddings.md"))
                .andExpect(jsonPath("$.sources[0].section").value("Признаковые алгоритмы"))
                .andExpect(jsonPath("$.sources[0].chunkId")
                        .value("articles/embeddings.md#fixed#0001"))
                .andExpect(jsonPath("$.quotes[0].text")
                        .value("Эмбеддинги — это векторы, а n-граммы становятся признаками."))
                .andExpect(jsonPath("$.answer").value("В документах говорится: «Эмбеддинги — это векторы…»"));
    }

    @Test
    void postEvaluateReturnsComplianceReport() throws Exception {
        when(service.evaluate()).thenReturn(new Day24EvalResponse(10, 10, 10, 10, 95.0,
                2, 2, "Комплаентность на 10 вопросах: источники 10/10, цитаты 10/10, "
                + "смысл ответа подтверждён цитатами 10/10 (средняя поддержка 95.0%). "
                + "Анти-галлюцинация: режим «не знаю» сработал на 2/2 слабых вопросах.",
                List.of(new Day24EvalItem("q01", "Что такое эмбеддинги?",
                        true, 1, true, 2, true, 100.0, false, List.of("embeddings.md"))),
                List.of(new Day24EvalWeakItem("w01", "Загадай число от одного до ста",
                        true, 0.1, List.of()))));

        mockMvc.perform(post("/api/day24/evaluate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.knownCount").value(10))
                .andExpect(jsonPath("$.sourcesPresent").value(10))
                .andExpect(jsonPath("$.quotesPresent").value(10))
                .andExpect(jsonPath("$.supportedCount").value(10))
                .andExpect(jsonPath("$.unknownTriggered").value(2))
                .andExpect(jsonPath("$.questions[0].hasSources").value(true))
                .andExpect(jsonPath("$.weak[0].unknown").value(true));
    }

    @Test
    void postAnswerMapsBlankQuestionToBadRequest() throws Exception {
        when(service.answer(any()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day24/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }

    @Test
    void postEvaluateMapsIndexFailureToServerError() throws Exception {
        when(service.evaluate())
                .thenThrow(new Day21IndexException("Не удалось прочитать индекс"));

        mockMvc.perform(post("/api/day24/evaluate"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Не удалось прочитать индекс"));
    }
}