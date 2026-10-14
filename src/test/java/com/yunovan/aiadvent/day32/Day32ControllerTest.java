package com.yunovan.aiadvent.day32;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day32Controller.class, ApiExceptionHandler.class})
class Day32ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day32ReviewService service;

    private static Day32HealthResponse health() {
        return new Day32HealthResponse("ok", "/repo", "main", "main", 6, 240, 900, 120_000,
                new Day32ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day32ReviewResponse review() {
        return new Day32ReviewResponse("main", "HEAD", "PR", 1,
                List.of(new Day32ChangedFile("src/main/java/Foo.java", "modified", 3, 1)),
                120, 3, 1, false,
                List.of(Day32Finding.of("high", "bug", "src/main/java/Foo.java", 12, "risk")),
                List.of(), List.of(), "summary", "## Потенциальные баги\n- risk",
                false, "qwen2.5:3b", 2, List.of("architecture.md"), List.of("architecture.md"),
                700L, 150, 60, 8.0, "");
    }

    @Test
    void healthReturnsIndexAndModel() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day32/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.codeFiles").value(240))
                .andExpect(jsonPath("$.model.installed").value(true));
    }

    @Test
    void changedReturnsDiffFileList() throws Exception {
        when(service.changedFiles(any(), any()))
                .thenReturn(List.of(new Day32ChangedFile("src/main/java/Foo.java", "modified", 3, 1)));

        mockMvc.perform(get("/api/day32/changed").param("base", "main").param("head", "HEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("src/main/java/Foo.java"))
                .andExpect(jsonPath("$[0].status").value("modified"));
    }

    @Test
    void reviewPostsRequest() throws Exception {
        when(service.review(any())).thenReturn(review());

        mockMvc.perform(post("/api/day32/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base\":\"main\",\"head\":\"HEAD\",\"title\":\"PR\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("summary"))
                .andExpect(jsonPath("$.bugs[0].severity").value("high"))
                .andExpect(jsonPath("$.fallback").value(false));
    }

    @Test
    void reviewCurrentForcesHeadlessRequest() throws Exception {
        when(service.review(any())).thenReturn(review());

        mockMvc.perform(post("/api/day32/review-current")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"base\":\"main\",\"head\":\"HEAD\"}"))
                .andExpect(status().isOk());

        ArgumentCaptor<Day32ReviewRequest> captor = ArgumentCaptor.forClass(Day32ReviewRequest.class);
        org.mockito.Mockito.verify(service).review(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().head()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().base()).isEqualTo("main");
    }

    @Test
    void reviewWhenGitFailsReturnsBadGateway() throws Exception {
        when(service.review(any())).thenThrow(new Day32GitException("git недоступен"));

        mockMvc.perform(post("/api/day32/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void changedWithBadRefReturnsBadRequest() throws Exception {
        when(service.changedFiles(any(), any()))
                .thenThrow(new IllegalArgumentException("неизвестная ветка"));

        mockMvc.perform(get("/api/day32/changed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("неизвестная ветка"));
    }
}
