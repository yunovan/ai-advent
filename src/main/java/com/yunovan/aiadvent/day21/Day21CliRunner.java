package com.yunovan.aiadvent.day21;

import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(Day21Properties.class)
public class Day21CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day21CliRunner.class);

    private final Day21IndexFacade facade;
    private final Day21AgentService service;
    private final Day21Properties properties;
    private final ApplicationContext applicationContext;

    public Day21CliRunner(Day21IndexFacade facade, Day21AgentService service,
                          Day21Properties properties, ApplicationContext applicationContext) {
        this.facade = facade;
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"21".equals(firstOption(args, "day"))) {
            log.info("Day 21 web UI: http://localhost:8080/day21.html  |  API: /api/day21/health, "
                    + "/api/day21/strategies, /api/day21/ingest, /api/day21/search, "
                    + "/api/day21/compare, /api/day21/chunks, /api/day21/agent  |  "
                    + "MCP-индекс: http://localhost:{}{}", properties.serverPort(), properties.path());
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day21HealthResponse health = facade.health();
                System.out.println("=== ИНДЕКС ДОКУМЕНТОВ ===");
                System.out.println("сервер: " + health.serverName() + " " + health.version());
                System.out.println("документов в корпусе: " + health.documents());
                System.out.println("объём: " + health.corpusChars() + " символов ≈ "
                        + health.pagesEstimate() + " страниц");
                for (Day21StrategyInfo strategy : health.strategies()) {
                    System.out.println("- " + strategy.name() + ": "
                            + (strategy.indexed() ? "построен" : "не построен") + ", "
                            + strategy.chunkCount() + " чанков");
                }
                maybeExit(args);
                return;
            }

            if (args.containsOption("ingest")) {
                String strategy = firstOption(args, "strategy");
                Day21IngestResponse response = facade.ingest(strategy);
                System.out.println("=== ПОСТРОЕНИЕ ИНДЕКСА ===");
                System.out.println("стратегия: " + response.strategy());
                System.out.println("документов: " + response.documents());
                System.out.println("чанков: " + response.chunks());
                System.out.println("объём: " + response.corpusChars() + " символов");
                System.out.println("файл индекса: " + response.indexFile());
                maybeExit(args);
                return;
            }

            String search = firstOption(args, "search");
            if (search != null) {
                int k = optionInt(args, "k", 3);
                Day21SearchResponse response = facade.search(
                        firstOption(args, "strategy"), search, k);
                System.out.println("=== ПОИСК ПО ИНДЕКСУ: " + search + " ===");
                System.out.println("стратегия: " + response.strategy());
                if (response.hits().isEmpty()) {
                    System.out.println("ничего не найдено");
                }
                for (int i = 0; i < response.hits().size(); i++) {
                    Day21SearchHit hit = response.hits().get(i);
                    System.out.println((i + 1) + ". [" + String.format(Locale.ROOT, "%.3f", hit.score()) + "] "
                            + hit.chunk().chunkId());
                    System.out.println("   source: " + hit.chunk().source());
                    System.out.println("   section: " + (hit.chunk().section().isEmpty()
                            ? "—" : hit.chunk().section()));
                    System.out.println("   «" + hit.snippet() + "»");
                }
                maybeExit(args);
                return;
            }

            if (args.containsOption("compare")) {
                Day21ComparisonResponse response = facade.compare();
                System.out.println("=== СРАВНЕНИЕ СТРАТЕГИЙ ЧАНКИНГА ===");
                for (Day21StrategyMetric metric : response.strategies()) {
                    System.out.println("Стратегия «" + metric.strategy() + "»:");
                    System.out.println("  чанков: " + metric.chunks()
                            + ", средний размер: " + String.format(Locale.ROOT, "%.0f", metric.avgChunkChars())
                            + ", min: " + String.format(Locale.ROOT, "%.0f", metric.minChunkChars())
                            + ", max: " + String.format(Locale.ROOT, "%.0f", metric.maxChunkChars())
                            + ", CV: " + String.format(Locale.ROOT, "%.2f", metric.coefficientOfVariation()));
                    System.out.println("  покрытие проб: " + String.format(Locale.ROOT, "%.1f",
                            metric.probeCoveragePercent())
                            + "% (" + metric.probeHits() + "/" + metric.probeTotal() + ")");
                }
                System.out.println("Вердикт: " + response.verdict());
                maybeExit(args);
                return;
            }

            if (args.containsOption("chunks")) {
                List<Day21Chunk> chunks = facade.chunks(firstOption(args, "strategy"));
                System.out.println("=== ЧАНКИ ИНДЕКСА ===");
                System.out.println("чанков: " + chunks.size());
                int limit = optionInt(args, "limit", 20);
                for (int i = 0; i < Math.min(limit, chunks.size()); i++) {
                    Day21Chunk chunk = chunks.get(i);
                    System.out.println("- " + chunk.chunkId() + " (" + chunk.charCount() + " симв.)");
                    System.out.println("  source: " + chunk.source()
                            + " | title: " + chunk.title());
                    System.out.println("  section: " + (chunk.section().isEmpty()
                            ? "—" : chunk.section()));
                }
                maybeExit(args);
                return;
            }

            String prompt = firstOption(args, "prompt");
            if (prompt != null && !prompt.isBlank()) {
                Day21AgentResponse response = service.submit(prompt);
                System.out.println("=== АГЕНТ / ИНДЕКС ===");
                System.out.println("prompt: " + response.prompt());
                System.out.println("intent: " + response.intent());
                System.out.println("tool: " + response.tool());
                System.out.println("arguments: " + response.arguments());
                System.out.println("tool result: " + response.toolResult());
                System.out.println("answer: ");
                System.out.println(response.answer());
                maybeExit(args);
                return;
            }
        } catch (Day21IndexException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Индекс документов доступен на http://localhost:8080/day21.html и /api/day21/agent");
        System.out.println("Проверка: --check | Поиск: --search=<запрос> [--strategy=fixed|structural --k=<k>]");
        System.out.println("Индекс: --ingest [--strategy=...] | Сравнение: --compare");
        System.out.println("Чанки: --chunks [--strategy=... --limit=<n>] | Запрос: --prompt=<текст>");
    }

    private void maybeExit(ApplicationArguments args) {
        if (args.containsOption("cli")) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private static String firstOption(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    private static int optionInt(ApplicationArguments args, String name, int fallback) {
        String value = firstOption(args, name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}