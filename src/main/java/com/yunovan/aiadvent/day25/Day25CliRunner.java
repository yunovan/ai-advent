package com.yunovan.aiadvent.day25;

import com.yunovan.aiadvent.day21.Day21IndexException;
import com.yunovan.aiadvent.day24.Day24Quote;
import com.yunovan.aiadvent.day24.Day24Source;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(Day25Properties.class)
public class Day25CliRunner implements ApplicationRunner {

    private static final String DEFAULT_SESSION = "cli";
    private static final List<String> EXIT_WORDS = List.of("выход", "выйти", "exit", "quit", "q");

    private final Day25ChatService service;
    private final Day25Properties properties;
    private final ApplicationContext applicationContext;

    public Day25CliRunner(Day25ChatService service, Day25Properties properties,
                          ApplicationContext applicationContext) {
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"25".equals(firstOption(args, "day"))) {
            return;
        }
        String sessionId = session(args);
        try {
            if (args.containsOption("check")) {
                printCheck(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("scenarios")) {
                printScenarios(service.scenarios());
                maybeExit(args);
                return;
            }

            String say = firstOption(args, "say");
            if (say != null && !say.isBlank()) {
                printTurn(service.chat(sessionId, say), true);
                maybeExit(args);
                return;
            }

            if (args.containsOption("chat")) {
                runRepl(sessionId, new BufferedReader(
                        new InputStreamReader(System.in, StandardCharsets.UTF_8)));
                maybeExit(args);
                return;
            }

            if (args.containsOption("evaluate") || args.containsOption("demo")) {
                printEvaluate(service.evaluate());
                maybeExit(args);
                return;
            }

            if (args.containsOption("history")) {
                printMemory(service.memory(sessionId));
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

        System.out.println("Мини-чат с RAG и памятью задачи: http://localhost:8080/day25.html");
        System.out.println("Состояние: --check | Сообщение: --say=\"<текст>\" | Диалог: --chat");
        System.out.println("Сессия: --session=<id> | Сценарии: --scenarios | Память: --history");
        System.out.println("Прогон сценариев: --demo | Проверка качества: --evaluate");
    }

    void runRepl(String sessionId, BufferedReader reader) {
        printMemory(service.memory(sessionId));
        while (true) {
            System.out.print("вы> ");
            System.out.flush();
            String line;
            try {
                line = reader.readLine();
            } catch (IOException ex) {
                return;
            }
            if (line == null) {
                System.out.println();
                return;
            }
            String message = line.trim();
            if (message.isEmpty()) {
                continue;
            }
            if (EXIT_WORDS.contains(message.toLowerCase(Locale.ROOT))) {
                printMemory(service.memory(sessionId));
                return;
            }
            try {
                printTurn(service.chat(sessionId, message), false);
            } catch (Day21IndexException | IllegalArgumentException ex) {
                System.out.println("ОШИБКА: " + ex.getMessage());
            }
        }
    }

    private void printCheck(Day25HealthResponse health) {
        System.out.println("=== МИНИ-ЧАТ С RAG И ПАМЯТЬЮ ЗАДАЧИ ===");
        System.out.println("документов в корпусе: " + health.documents());
        System.out.println("объём: " + health.corpusChars() + " символов ≈ " + health.pagesEstimate()
                + " страниц");
        System.out.println("стратегия поиска: " + health.strategy());
        System.out.println("топ-K до/после: " + health.topKBefore() + "/" + health.topKAfter());
        System.out.println("порог фильтра: " + String.format(Locale.ROOT, "%.2f", health.threshold())
                + " | порог «не знаю»: "
                + String.format(Locale.ROOT, "%.2f", health.unknownThreshold()));
        System.out.println("цитат на источник: " + health.quotesPerSource()
                + " | поддержка ответа ≥ "
                + String.format(Locale.ROOT, "%.0f", health.supportThreshold() * 100) + "%");
        System.out.println("история диалога: до " + health.historyLimit() + " сообщений");
        System.out.println("сессий в памяти: " + health.sessions() + " / " + health.maxSessions()
                + " | терминов в памяти задачи: " + health.memoryTermsLimit());
        System.out.println("сценарии: " + String.join(" | ", health.scenarios()));
    }

    private void printTurn(Day25ChatTurn turn, boolean verbose) {
        System.out.println("[" + turn.sessionId() + " #" + turn.turn() + "] вы: " + turn.userMessage());
        if (verbose) {
            System.out.println("запрос для поиска (с памятью задачи): " + turn.matchedQuery()
                    + (turn.rewritten() ? " (переписан)" : ""));
            System.out.println("кандидатов до фильтрации: " + turn.candidatesBefore()
                    + " | отсеяно: " + turn.filteredOut());
        }
        System.out.println("ассистент: " + turn.reply());
        System.out.println("источники: " + (turn.sources().isEmpty() ? "—" : turn.sources().size() + " шт."));
        for (Day24Source source : turn.sources()) {
            System.out.println("  - " + source.source() + " | раздел «" + source.section()
                    + "» | чанк " + source.chunkId() + " | score "
                    + String.format(Locale.ROOT, "%.3f", source.score()));
        }
        System.out.println("цитаты: " + (turn.quotes().isEmpty() ? "—" : turn.quotes().size() + " шт."));
        for (Day24Quote quote : turn.quotes()) {
            System.out.println("  * «" + quote.text() + "»");
            System.out.println("    [" + quote.source() + " | «" + quote.section() + "» | "
                    + quote.chunkId() + "]");
        }
        System.out.println("память задачи: цель «" + turn.memory().goal() + "» | уточнений: "
                + turn.memory().clarifications().size() + " | ограничений: "
                + turn.memory().constraints().size() + " | терминов: "
                + turn.memory().terms().size() + " | история: " + turn.historySize());
        System.out.println("поддержка цитатами: " + String.format(Locale.ROOT, "%.1f",
                turn.supportCoveragePercent()) + "% | «не знаю»: " + (turn.unknown() ? "ДА" : "нет")
                + " | LLM: " + (turn.fallback() ? "недоступен, ответ из цитат" : "отвечал"));
        System.out.println();
    }

    private void printMemory(Day25SessionView view) {
        System.out.println("=== ПАМЯТЬ ЗАДАЧИ [" + view.sessionId() + "] ===");
        System.out.println("сообщений: " + view.historySize() + " (ходов: " + view.turns() + ")");
        System.out.println("цель: " + (view.goal() == null ? "пока не задана" : view.goal()));
        Day25TaskMemory memory = service.taskMemory(view.sessionId());
        if (memory.clarifications().isEmpty()) {
            System.out.println("уточнения: —");
        } else {
            System.out.println("уточнения: " + String.join(" | ", memory.clarifications()));
        }
        if (memory.constraints().isEmpty()) {
            System.out.println("ограничения: —");
        } else {
            System.out.println("ограничения: " + String.join(" | ", memory.constraints()));
        }
        System.out.println("термины: " + (memory.terms().isEmpty() ? "—" : String.join(", ", memory.terms())));
    }

    private void printScenarios(List<Day25Scenario> scenarios) {
        System.out.println("=== СЦЕНАРИИ ДИАЛОГОВ (" + scenarios.size() + ") ===");
        for (Day25Scenario scenario : scenarios) {
            System.out.println(scenario.id() + " | " + scenario.title());
            System.out.println("   цель: " + scenario.goal());
            System.out.println("   ограничения: " + String.join("; ", scenario.expectedConstraints()));
            System.out.println("   сообщений: " + scenario.messages().size());
            for (int i = 0; i < scenario.messages().size(); i++) {
                System.out.println("   " + (i + 1) + ". " + scenario.messages().get(i));
            }
        }
    }

    private void printEvaluate(Day25EvalResponse evaluate) {
        System.out.println("=== ДЛИННЫЕ СЦЕНАРИИ: ПАМЯТЬ ЗАДАЧИ + ИСТОЧНИКИ ===");
        System.out.println("сценариев: " + evaluate.scenariosCount() + " | сообщений: "
                + evaluate.totalTurns());
        System.out.println("ответы с источниками: " + evaluate.turnsWithSources() + "/"
                + evaluate.totalTurns());
        System.out.println("ответы с цитатами: " + evaluate.quotesTurns() + "/"
                + evaluate.totalTurns());
        System.out.println("цель диалога удержана: " + evaluate.goalRetainedTurns() + "/"
                + evaluate.totalTurns());
        System.out.println("ограничения удержаны: " + evaluate.constraintsKeptTurns() + "/"
                + evaluate.totalTurns());
        System.out.println("средняя поддержка ответа цитатами: "
                + String.format(Locale.ROOT, "%.1f", evaluate.avgSupportPercent()) + "%");
        System.out.println("режим «не знаю»: " + evaluate.unknownTurns() + " ходов");
        System.out.println("Вердикт: " + evaluate.verdict());
        for (Day25ScenarioResult result : evaluate.scenarios()) {
            System.out.println("- " + result.id() + " | " + result.title());
            System.out.println("  цель: " + result.goal());
            System.out.println("  ходов: " + result.turns() + " | с источниками: "
                    + result.turnsWithSources() + " | цель удержана: " + result.goalRetainedTurns()
                    + " | ограничения удержаны: " + result.constraintsKeptTurns()
                    + " | «не знаю»: " + result.unknownTurns());
            System.out.println("  итоговая память: " + result.finalMemory().goal()
                    + " | термины: " + String.join(", ", result.finalMemory().terms()));
            for (Day25ScenarioTurn turn : result.details()) {
                System.out.println("    #" + turn.turn() + " | " + turn.message());
                System.out.println("      источники: " + (turn.hasSources() ? "да ("
                        + turn.sourcesCount() + ")" : "НЕТ") + " | цитаты: " + turn.quotesCount()
                        + " | цель: " + (turn.goalRetained() ? "удержана" : "ПОТЕРЯНА")
                        + " | ограничения: " + (turn.constraintsKept() ? "в памяти" : "ПОТЕРЯНЫ")
                        + " | score " + String.format(Locale.ROOT, "%.3f", turn.bestScore()));
            }
        }
    }

    private String session(ApplicationArguments args) {
        String sessionId = firstOption(args, "session");
        return sessionId == null || sessionId.isBlank() ? DEFAULT_SESSION : sessionId.trim();
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
}
