package com.yunovan.aiadvent.day21;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day21Controller.class, ApiExceptionHandler.class})
class Day21ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day21IndexFacade facade;

    @MockitoBean
    private Day21AgentService service;

    @Test
    void getHealthReturnsIndexStatus() throws Exception {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "ai-advent-index-mcp", "0.1.0", 9, 131000, 44,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 123),
                        new Day21StrategyInfo("structural", "описание", false, 0))));

        mockMvc.perform(get("/api/day21/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverName").value("ai-advent-index-mcp"))
                .andExpect(jsonPath("$.documents").value(9))
                .andExpect(jsonPath("$.pagesEstimate").value(44))
                .andExpect(jsonPath("$.strategies.length()").value(2))
                .andExpect(jsonPath("$.strategies[0].indexed").value(true))
                .andExpect(jsonPath("$.strategies[1].indexed").value(false));
    }

    @Test
    void getStrategiesReturnsBothStrategies() throws Exception {
        when(facade.strategyInfo(eq("fixed")))
                .thenReturn(new Day21StrategyInfo("fixed", "описание", true, 10));
        when(facade.strategyInfo(eq("structural")))
                .thenReturn(new Day21StrategyInfo("structural", "описание", false, 0));

        mockMvc.perform(get("/api/day21/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("structural"));
    }

    @Test
    void postIngestBuildsIndex() throws Exception {
        when(facade.ingest(any())).thenReturn(new Day21IngestResponse(
                "fixed", 9, 123, 131000, "data/day21-index/index-fixed.json"));

        mockMvc.perform(post("/api/day21/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"strategy\":\"fixed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("fixed"))
                .andExpect(jsonPath("$.documents").value(9))
                .andExpect(jsonPath("$.chunks").value(123));
    }

    @Test
    void postSearchReturnsHits() throws Exception {
        when(facade.search(eq("fixed"), eq("эмбеддинги"), eq(3))).thenReturn(
                new Day21SearchResponse("fixed", "эмбеддинги", 3,
                        List.of(new Day21SearchHit(
                                new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                                        "Эмбеддинги", "Признаковые алгоритмы",
                                        "articles/embeddings.md#fixed#0001", 0, 100, "текст"),
                                0.9, "сниппет"))));

        mockMvc.perform(post("/api/day21/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"эмбеддинги\",\"strategy\":\"fixed\",\"k\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hits.length()").value(1))
                .andExpect(jsonPath("$.hits[0].score").value(0.9))
                .andExpect(jsonPath("$.hits[0].chunk.fileName").value("embeddings.md"));
    }

    @Test
    void getCompareReturnsVerdict() throws Exception {
        when(facade.compare()).thenReturn(new Day21ComparisonResponse(
                List.of(new Day21StrategyMetric(
                        "fixed", 9, 100, 130000, 500.0, 300.0, 900.0, 130.0, 0.26, 5, 6, 83.3)),
                List.of("запрос"), "Лучше fixed"));

        mockMvc.perform(get("/api/day21/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verdict").value("Лучше fixed"));
    }

    @Test
    void getChunksReturnsChunkList() throws Exception {
        when(facade.chunks(any())).thenReturn(List.of(
                new Day21Chunk("fixed", "articles/chunking.md", "chunking.md",
                        "Чанкинг", "Зачем нужны чанки", "articles/chunking.md#fixed#0001",
                        0, 120, "текст")));

        mockMvc.perform(get("/api/day21/chunks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].chunkId").value("articles/chunking.md#fixed#0001"))
                .andExpect(jsonPath("$[0].section").value("Зачем нужны чанки"));
    }

    @Test
    void postAgentRunsThePrompt() throws Exception {
        when(service.submit(eq("найди эмбеддинги"))).thenReturn(new Day21AgentResponse(
                "найди эмбеддинги", "search", "index_search",
                Map.of("query", "эмбеддинги", "strategy", "fixed"),
                "Найдено по запросу", "Ответ агента"));

        mockMvc.perform(post("/api/day21/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"найди эмбеддинги\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("search"))
                .andExpect(jsonPath("$.tool").value("index_search"))
                .andExpect(jsonPath("$.answer").value("Ответ агента"));
    }

    @Test
    void postSearchMapsInvalidQueryToBadRequest() throws Exception {
        when(facade.search(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Запрос не может быть пустым"));

        mockMvc.perform(post("/api/day21/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Запрос не может быть пустым"));
    }

    @Test
    void postAgentMapsUnknownPromptToBadRequest() throws Exception {
        when(service.submit(any()))
                .thenThrow(new IllegalArgumentException("Я работаю с локальным индексом"));

        mockMvc.perform(post("/api/day21/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"болтовня\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Я работаю с локальным индексом"));
    }
}