package com.yunovan.aiadvent.day12;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day11.Day11MemoryEntry;
import com.yunovan.aiadvent.day11.Day11MemoryLayer;
import com.yunovan.aiadvent.llm.LlmException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day12DialogController.class, ApiExceptionHandler.class})
class Day12DialogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day12DialogService service;

    private static final Day12Profile ASYA = new Day12Profile(
            "asya", "Ася", "кратко", "списки", List.of("без эмодзи"), "аналитик", Instant.now());

    private static Day12DialogInfo info(String dialogId) {
        return new Day12DialogInfo(
                dialogId, Instant.now(), null, null, 0, List.of(),
                ASYA,
                List.of(new Day11MemoryEntry("Бюджет", "10 000$", Day11MemoryLayer.WORKING, "manual", false, null)),
                List.of(new Day11MemoryEntry("Имя", "Ася", Day11MemoryLayer.LONG_TERM, "manual", false, null)));
    }

    @Test
    void postDialogsStartsWithProfile() throws Exception {
        when(service.start("Менеджер")).thenReturn(new Day12StartResponse(
                "d1", Instant.now(), ASYA, List.of(ASYA)));

        mockMvc.perform(post("/api/day12/dialogs").param("profile", "Менеджер"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.profile.id").value("asya"))
                .andExpect(jsonPath("$.profiles[0].name").value("Ася"));
    }

    @Test
    void postChatWithProfileReturnsAnswerAndMemory() throws Exception {
        when(service.chat("d1", "Соберём ТЗ", 1000L)).thenReturn(new Day12ChatResponse(
                "d1", "Соберём ТЗ", "Отлично!", "gpt-4o-mini", 2, 120L,
                20, 5, 30, 10, 15, 8, 55, 1000L, false,
                ASYA,
                List.of(ConversationMessage.user("Соберём ТЗ"), ConversationMessage.assistant("Отлично!")),
                List.of(new Day11MemoryEntry("Бюджет", "10 000$", Day11MemoryLayer.WORKING, "manual", false, null)),
                List.of(new Day11MemoryEntry("Имя", "Ася", Day11MemoryLayer.LONG_TERM, "manual", false, null))));

        mockMvc.perform(post("/api/day12/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Соберём ТЗ\",\"contextLimit\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.profile.id").value("asya"))
                .andExpect(jsonPath("$.profile.name").value("Ася"))
                .andExpect(jsonPath("$.workingTokens").value(10))
                .andExpect(jsonPath("$.longTermTokens").value(15))
                .andExpect(jsonPath("$.working[0].value").value("10 000$"))
                .andExpect(jsonPath("$.longTerm[0].value").value("Ася"))
                .andExpect(jsonPath("$.exceeded").value(false));
    }

    @Test
    void postChatMapsLlmFailureToBadGateway() throws Exception {
        when(service.chat(any(), any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day12/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postChatMapsUnknownDialogToNotFound() throws Exception {
        when(service.chat(any(), any(), any())).thenThrow(new DialogNotFoundException("d1"));

        mockMvc.perform(post("/api/day12/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Диалог 'd1' не найден"));
    }

    @Test
    void postSetProfileSwitchesDialogProfile() throws Exception {
        when(service.setProfile("d1", "dev")).thenReturn(info("d1"));

        mockMvc.perform(post("/api/day12/dialogs/d1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":\"dev\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.id").value("asya"));
    }

    @Test
    void postRememberSavesIntoChosenLayer() throws Exception {
        when(service.remember("d1", "Стек", "Java 21", "long-term")).thenReturn(info("d1"));

        mockMvc.perform(post("/api/day12/dialogs/d1/remember")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"Стек\",\"value\":\"Java 21\",\"layer\":\"long-term\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.longTerm[0].value").value("Ася"));
    }

    @Test
    void postFinishReturnsSummaryAndProfile() throws Exception {
        when(service.finish("d1")).thenReturn(
                new Day12FinishResponse("d1", Instant.now(), "Итог диалога", 6, "asya", 3));

        mockMvc.perform(post("/api/day12/dialogs/d1/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Итог диалога"))
                .andExpect(jsonPath("$.profileId").value("asya"))
                .andExpect(jsonPath("$.longTermEntryCount").value(3));
    }

    @Test
    void getDialogsListsAll() throws Exception {
        when(service.dialogs()).thenReturn(List.of(info("d1"), info("d2")));

        mockMvc.perform(get("/api/day12/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].profile.name").value("Ася"));
    }

    @Test
    void getProfilesListsAvailable() throws Exception {
        when(service.profiles()).thenReturn(List.of(
                new Day12Profile("asya", "Ася", "кратко", "списки", List.of(), "", Instant.now()),
                new Day12Profile("dev", "Разработчик", "точно", "код", List.of(), "", Instant.now())));

        mockMvc.perform(get("/api/day12/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("asya"))
                .andExpect(jsonPath("$[1].name").value("Разработчик"));
    }

    @Test
    void postCreateProfileCreatesNew() throws Exception {
        when(service.createProfile("Тестировщик", "аккуратно", "чек-листы",
                List.of("без абстракций"), "QA-инженер"))
                .thenReturn(new Day12Profile(
                        "testirovshchik", "Тестировщик", "аккуратно", "чек-листы",
                        List.of("без абстракций"), "QA-инженер", Instant.now()));

        mockMvc.perform(post("/api/day12/profiles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Тестировщик\",\"style\":\"аккуратно\","
                                + "\"format\":\"чек-листы\",\"restrictions\":[\"без абстракций\"],"
                                + "\"notes\":\"QA-инженер\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("testirovshchik"))
                .andExpect(jsonPath("$.name").value("Тестировщик"));
    }

    @Test
    void getProfileByIdReturnsIt() throws Exception {
        when(service.profile("dev")).thenReturn(new Day12Profile(
                "dev", "Разработчик", "точно", "код", List.of(), "", Instant.now()));

        mockMvc.perform(get("/api/day12/profiles/dev"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("dev"))
                .andExpect(jsonPath("$.name").value("Разработчик"));
    }

    @Test
    void getProfileUnknownReturnsNotFound() throws Exception {
        when(service.profile("missing")).thenReturn(null);

        mockMvc.perform(get("/api/day12/profiles/missing"))
                .andExpect(status().isNotFound());
    }
}