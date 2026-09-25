package com.yunovan.aiadvent.day19;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day19CliRunnerTest {

    private final ApplicationContext context = mock(ApplicationContext.class);

    private String run(Day19MarketApi market, Day19AgentService service, Day19SaveService saveService,
                       String... args) {
        Day19CliRunner runner = new Day19CliRunner(market, service, saveService,
                new Day19Properties(9092, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", "dir"), context);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    @Test
    void checkPrintsConnectionInfo() {
        Day19AgentService service = mock(Day19AgentService.class);
        when(service.health()).thenReturn(new Day19HealthResponse(true, "ai-advent-pipeline-mcp", "0.1.0", 3));

        String output = run(mock(Day19MarketApi.class), service, mock(Day19SaveService.class),
                "--day=19", "--check");

        assertThat(output).contains("=== СОЕДИНЕНИЕ С MCP-ПАЙПЛАЙНОМ ===");
        assertThat(output).contains("connected: true");
        assertThat(output).contains("ai-advent-pipeline-mcp");
        assertThat(output).contains("tools: 3");
    }

    @Test
    void toolsPrintsToolList() {
        Day19AgentService service = mock(Day19AgentService.class);
        when(service.tools()).thenReturn(List.of(
                new Day19ToolInfo("search", "Ищет товары."),
                new Day19ToolInfo("summarize", "Строит сводную таблицу."),
                new Day19ToolInfo("saveToFile", "Сохраняет файл.")));

        String output = run(mock(Day19MarketApi.class), service, mock(Day19SaveService.class),
                "--day=19", "--tools");

        assertThat(output).contains("=== ИНСТРУМЕНТЫ MCP ===");
        assertThat(output).contains("search");
        assertThat(output).contains("saveToFile");
    }

    @Test
    void searchPrintsFoundProducts() {
        Day19MarketApi market = mock(Day19MarketApi.class);
        when(market.search("ноутбук", null, 0, null)).thenReturn(List.of(
                new Day19Product("p01", "Ноутбук Lenovo IdeaPad 3", "ноутбуки", "DNS",
                        "https://www.dns-shop.ru/lenovo-ideapad-3", 54990, "RUB", 4.6, Map.of("Память", "16 ГБ"))));

        String output = run(market, mock(Day19AgentService.class), mock(Day19SaveService.class),
                "--day=19", "--search=ноутбук");

        assertThat(output).contains("=== ПОИСК: ноутбук ===");
        assertThat(output).contains("найдено товаров: 1");
        assertThat(output).contains("Ноутбук Lenovo IdeaPad 3");
        assertThat(output).contains("54990");
        verify(market).search("ноутбук", null, 0, null);
    }

    @Test
    void summarizePrintsTable() {
        Day19MarketApi market = mock(Day19MarketApi.class);
        when(market.summarizeQuery("телевизоры", "csv"))
                .thenReturn("Товар;Продавец;Цена\nТелевизор Samsung;Citilink;59990");

        String output = run(market, mock(Day19AgentService.class), mock(Day19SaveService.class),
                "--day=19", "--summarize=телевизоры", "--format=csv");

        assertThat(output).contains("=== СВОДНАЯ ТАБЛИЦА: телевизоры ===");
        assertThat(output).contains("Телевизор Samsung");
        verify(market).summarizeQuery("телевизоры", "csv");
    }

    @Test
    void savePrintsFileInfo() {
        Day19MarketApi market = mock(Day19MarketApi.class);
        when(market.saveQuery("телевизоры", "csv", "тв-2026")).thenReturn(
                new Day19SavedFile("тв-2026.csv", "data/day19-market/тв-2026.csv", "csv", 321, null));

        String output = run(market, mock(Day19AgentService.class), mock(Day19SaveService.class),
                "--day=19", "--save=телевизоры", "--format=csv", "--file=тв-2026");

        assertThat(output).contains("=== СОХРАНЕНИЕ ФАЙЛА ===");
        assertThat(output).contains("файл: тв-2026.csv");
        assertThat(output).contains("размер: 321 байт");
    }

    @Test
    void pipelinePrintsStepResults() {
        Day19AgentService service = mock(Day19AgentService.class);
        when(service.pipeline("ноутбуки", "markdown", "laptops")).thenReturn(
                new Day19PipelineResponse("ноутбуки", "markdown", List.of(
                        new Day19PipelineStep("search", true, "получено 5 товаров"),
                        new Day19PipelineStep("summarize", true, "таблица готова к сохранению"),
                        new Day19PipelineStep("saveToFile", true, "файл записан")),
                        new Day19SavedFile("laptops.md", "data/day19-market/laptops.md", "markdown", 410, null)));

        String output = run(mock(Day19MarketApi.class), service, mock(Day19SaveService.class),
                "--day=19", "--pipeline=ноутбуки", "--format=markdown", "--file=laptops");

        assertThat(output).contains("=== ПАЙПЛАЙН: search -> summarize -> saveToFile ===");
        assertThat(output).contains("search: OK");
        assertThat(output).contains("summarize: OK");
        assertThat(output).contains("saveToFile: OK");
        assertThat(output).contains("файл: laptops.md");
        assertThat(output).contains("размер: 410 байт");
        verify(service).pipeline("ноутбуки", "markdown", "laptops");
    }

    @Test
    void promptSubmitsAgentAndPrintsAnswer() {
        Day19AgentService service = mock(Day19AgentService.class);
        when(service.submit("сравни ноутбуки в таблицу"))
                .thenReturn(new Day19AgentResponse("сравни ноутбуки в таблицу",
                        "compare", "summarize", Map.of("query", "ноутбуки"),
                        "Сравнение по запросу «ноутбуки»", "Таблица выше!"));

        String output = run(mock(Day19MarketApi.class), service, mock(Day19SaveService.class),
                "--day=19", "--prompt=сравни ноутбуки в таблицу");

        assertThat(output).contains("=== АГЕНТ / MCP ===");
        assertThat(output).contains("intent: compare");
        assertThat(output).contains("tool: summarize");
        assertThat(output).contains("Таблица выше!");
        verify(service).submit("сравни ноутбуки в таблицу");
    }

    @Test
    void filesPrintsSavedList() {
        Day19SaveService saveService = mock(Day19SaveService.class);
        when(saveService.list()).thenReturn(List.of(
                new Day19SavedFile("laptops.md", "data/day19-market/laptops.md", "markdown", 410, null)));

        String output = run(mock(Day19MarketApi.class), mock(Day19AgentService.class), saveService,
                "--day=19", "--files");

        assertThat(output).contains("=== СОХРАНЁННЫЕ ФАЙЛЫ ===");
        assertThat(output).contains("файлов: 1");
        assertThat(output).contains("laptops.md");
    }

    @Test
    void noKnownFlagsPrintsUsage() {
        String output = run(mock(Day19MarketApi.class), mock(Day19AgentService.class),
                mock(Day19SaveService.class), "--day=19");

        assertThat(output).contains("--search");
        assertThat(output).contains("--summarize");
        assertThat(output).contains("--pipeline");
        assertThat(output).contains("--prompt");
    }

    @Test
    void suppressedWhenDayIsNotNineteen() {
        Day19AgentService service = mock(Day19AgentService.class);

        String output = run(mock(Day19MarketApi.class), service, mock(Day19SaveService.class),
                "--day=18", "--check", "--tools");

        assertThat(output).doesNotContain("СОЕДИНЕНИЕ С MCP-ПАЙПЛАЙНОМ");
        assertThat(output).doesNotContain("ИНСТРУМЕНТЫ MCP");
    }

    @Test
    void pipelineErrorPrintsMessage() {
        Day19AgentService service = mock(Day19AgentService.class);
        when(service.pipeline(any(), any(), isNull())).thenThrow(new Day19McpException("MCP недоступен"));

        String output = run(mock(Day19MarketApi.class), service, mock(Day19SaveService.class),
                "--day=19", "--pipeline=ноутбуки");

        assertThat(output).contains("ОШИБКА: MCP недоступен");
    }
}