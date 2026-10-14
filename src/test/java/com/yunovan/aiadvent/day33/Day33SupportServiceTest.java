package com.yunovan.aiadvent.day33;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26InstalledModel;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day33SupportServiceTest {

    private final Day33Properties properties = new Day33Properties(null, null, null, null,
            "data/day33-none", "build/day33-none", 5, 12, 700, 80, 400, 0.2);
    private final Day33CrmStore crm = new Day33CrmStore(properties);
    private final Day33SupportIndex index = new Day33SupportIndex(properties, new Day21EmbeddingService(
            new Day21Properties(null, null, null, null, null, null, null, null, null)));
    private final Day33McpClient mcp = mock(Day33McpClient.class);
    private final Day26LocalLlmClient llm = mock(Day26LocalLlmClient.class);
    private final Day26Properties llmProperties = new Day26Properties(null, null, null, null, null, null);

    private Day33SupportService service;

    @BeforeEach
    void setUp() {
        service = new Day33SupportService(properties, index, crm, mcp, llm, llmProperties);
    }

    private void stubMcpAvailable() {
        when(mcp.connect()).thenReturn(new Day33Connection("2024-11-05", "ai-advent-crm-mcp", "0.1.0", "s"));
        when(mcp.listTools(any())).thenReturn(List.of(
                new Day33ToolInfo("crm_user", "профиль"),
                new Day33ToolInfo("crm_ticket", "обращение"),
                new Day33ToolInfo("crm_user_tickets", "обращения пользователя"),
                new Day33ToolInfo("crm_search_tickets", "поиск"),
                new Day33ToolInfo("crm_open_tickets", "открытые")));
        when(mcp.callTool(any(), anyString(), any())).thenAnswer(invocation -> {
            String tool = invocation.getArgument(1);
            return new Day33ToolResult(tool, "контекст " + tool, false);
        });
    }

    private void stubModelAvailable() {
        when(llm.version()).thenReturn("0.35.1");
        when(llm.models()).thenReturn(List.of(
                new Day26InstalledModel("qwen2.5:3b", 1L, "3.1B", "Q4_K_M")));
    }

    @Test
    void healthReportsKnowledgeBaseCrmAndMcp() {
        stubMcpAvailable();
        stubModelAvailable();

        Day33HealthResponse health = service.health();

        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.documents()).contains("authorization.md");
        assertThat(health.users()).isEqualTo(4);
        assertThat(health.tickets()).isEqualTo(5);
        assertThat(health.mcp().available()).isTrue();
        assertThat(health.mcp().tools()).isEqualTo(5);
        assertThat(health.model().available()).isTrue();
        assertThat(health.model().installed()).isTrue();
    }

    @Test
    void healthMarksMcpUnavailable() {
        when(mcp.connect()).thenThrow(new Day33McpException("нет сервера"));
        stubModelAvailable();

        Day33HealthResponse health = service.health();

        assertThat(health.mcp().available()).isFalse();
        assertThat(health.mcp().reason()).contains("нет сервера");
    }

    @Test
    void kbListsDocuments() {
        List<Day33DocumentInfo> kb = service.kb();

        assertThat(kb).isNotEmpty();
        assertThat(kb).extracting(Day33DocumentInfo::fileName).contains("faq.md");
        assertThat(kb.getFirst().chunks()).isPositive();
    }

    @Test
    void usersAndTicketsComeFromCrm() {
        assertThat(service.users()).hasSize(4);
        assertThat(service.users()).extracting(Day33UserInfo::id).contains("U-1001");
        assertThat(service.users().stream().filter(u -> u.id().equals("U-1001")).findFirst()
                .orElseThrow().tickets()).isEqualTo(2);

        assertThat(service.tickets()).hasSize(5);
        assertThat(service.tickets()).extracting(Day33TicketInfo::id).contains("T-1001");
    }

    @Test
    void toolsComeFromMcpServer() {
        stubMcpAvailable();

        Day33ToolsResponse tools = service.tools();

        assertThat(tools.available()).isTrue();
        assertThat(tools.count()).isEqualTo(5);
    }

    @Test
    void crmDelegatesToMcpTool() {
        stubMcpAvailable();

        Day33CrmResponse response = service.crm("ticket", Map.of("ticketId", "T-1001"));

        assertThat(response.tool()).isEqualTo("crm_ticket");
        assertThat(response.ok()).isTrue();
        assertThat(response.content()).contains("crm_ticket");
    }

    @Test
    void crmRejectsUnknownTool() {
        assertThatThrownBy(() -> service.crm("mystery", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void askUsesTicketContextAndKnowledgeBase() {
        stubMcpAvailable();
        when(llm.chat(anyList(), any(Day26ChatOptions.class))).thenReturn(new Day26Answer(
                "prompt", "Проверьте синхронизацию времени и 2FA. Источник: authorization.md.",
                "qwen2.5:3b", "http://localhost:11434", 900L, 120, 40, 9.5));

        Day33SupportResponse response = service.ask("Почему не работает авторизация?", "T-1001", null);

        assertThat(response.fallback()).isFalse();
        assertThat(response.ticketId()).isEqualTo("T-1001");
        assertThat(response.userId()).isEqualTo("U-1001");
        assertThat(response.userName()).isEqualTo("Иван Петров");
        assertThat(response.ticketSubject()).isEqualTo("Не работает авторизация");
        assertThat(response.mcpUsed()).isTrue();
        assertThat(response.mcpTools()).isEqualTo(5);
        assertThat(response.retrieved()).isPositive();
        assertThat(response.sourceFiles()).isNotEmpty();
        assertThat(response.latencyMs()).isEqualTo(900L);
        assertThat(response.groundingPercent()).isNotNull();
    }

    @Test
    void askWithoutTicketUsesUserContext() {
        stubMcpAvailable();
        when(llm.chat(anyList(), any(Day26ChatOptions.class))).thenReturn(new Day26Answer(
                "prompt", "Ответ", "qwen2.5:3b", "http://localhost:11434", 500L, 80, 20, 7.0));

        Day33SupportResponse response = service.ask("Как оплатить счёт?", null, "U-1002");

        assertThat(response.ticketId()).isEmpty();
        assertThat(response.userId()).isEqualTo("U-1002");
        assertThat(response.userName()).isEqualTo("Мария Соколова");
        assertThat(response.mcpUsed()).isTrue();
    }

    @Test
    void askFallsBackWhenModelUnavailable() {
        stubMcpAvailable();
        when(llm.chat(anyList(), any(Day26ChatOptions.class)))
                .thenThrow(new Day26LlmException("Ollama недоступна"));

        Day33SupportResponse response = service.ask("Почему не работает авторизация?", "T-1001", null);

        assertThat(response.fallback()).isTrue();
        assertThat(response.answer()).contains("базы знаний");
        assertThat(response.note()).contains("Ollama недоступна");
    }

    @Test
    void askWithoutMcpStillAnswersFromKnowledgeBase() {
        when(mcp.connect()).thenThrow(new Day33McpException("нет сервера"));
        when(llm.chat(anyList(), any(Day26ChatOptions.class))).thenReturn(new Day26Answer(
                "prompt", "Ответ", "qwen2.5:3b", "http://localhost:11434", 400L, 50, 10, 5.0));

        Day33SupportResponse response = service.ask("Почему не работает авторизация?", null, "U-1001");

        assertThat(response.mcpUsed()).isFalse();
        assertThat(response.answer()).isEqualTo("Ответ");
    }

    @Test
    void askRejectsBlankQuestion() {
        assertThatThrownBy(() -> service.ask("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
