package com.yunovan.aiadvent.day22;

import com.yunovan.aiadvent.day21.Day21IndexException;
import com.yunovan.aiadvent.day21.Day21StrategyInfo;
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
@EnableConfigurationProperties(Day22Properties.class)
public class Day22CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day22CliRunner.class);

    private final Day22RagService service;
    private final Day22Properties properties;
    private final ApplicationContext applicationContext;

    public Day22CliRunner(Day22RagService service, Day22Properties properties,
                          ApplicationContext applicationContext) {
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"22".equals(firstOption(args, "day"))) {
            log.info("Day 22 web UI: http://localhost:8080/day22.html  |  API: /api/day22/health, "
                    + "/api/day22/questions, /api/day22/answer, /api/day22/ask, "
                    + "/api/day22/compare, /api/day22/evaluate");
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day22HealthResponse health = service.health();
                System.out.println("=== RAG / ИНДЕКС ===");
                System.out.println("документов в корпусе: " + health.documents());
                System.out.println("объём: " + health.corpusChars() + " символов ≈ "
                        + health.pagesEstimate() + " страниц");
                System.out.println("стратегия поиска: " + health.strategy()
                        + " | top-K: " + health.topK()
                        + " | ответ до " + health.answerMaxTokens() + " токенов");
                maybeExit(args);
                return;
            }

            String answer = firstOption(args, "answer");
            if (answer != null && !answer.isBlank()) {
                printAnswer(service.answer(answer, firstOption(args, "mode")));
                maybeExit(args);
                return;
            }

            String compare = firstOption(args, "compare");
            if (compare != null && !compare.isBlank()) {
                printCompare(service.compare(compare));
                maybeExit(args);
                return;
            }

            if (args.containsOption("questions")) {
                printQuestions(service.questions());
                maybeExit(args);
                return;
            }

            if (args.containsOption("evaluate")) {
                printEvaluate(service.evaluate());
                maybeExit(args);
                return;
            }

            String prompt = firstOption(args, "prompt");
            if (prompt != null && !prompt.isBlank()) {
                printAnswer(service.ask(prompt));
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

        System.out.println("Первый RAG-запрос доступен на http://localhost:8080/day22.html");
        System.out.println("Проверка: --check | Ответ: --answer=<вопрос> [--mode=rag|plain]");
        System.out.println("Сравнение режимов: --compare=<вопрос> | Контрольные вопросы: --questions");
        System.out.println("Качество на 10 вопросах: --evaluate | Агент: --prompt=<вопрос>");
    }

    private void printAnswer(Day22AnswerResponse response) {
        System.out.println("=== ОТВЕТ АГЕНТА (" + response.mode().toUpperCase(Locale.ROOT) + ") ===");
        System.out.println("вопрос: " + response.question());
        System.out.println("режим: " + response.mode());
        List<String> sources = response.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName()).distinct().toList();
        System.out.println("источники: " + (sources.isEmpty() ? "—"
                : String.join(", ", sources)));
        System.out.println("fallback (LLM недоступен): " + (response.fallback() ? "да" : "нет"));
        System.out.println("ответ:");
        System.out.println(response.answer());
    }

    private void printCompare(Day22CompareResponse response) {
        System.out.println("=== СРАВНЕНИЕ: С RAG / БЕЗ RAG ===");
        System.out.println("вопрос: " + response.question());
        System.out.println("С RAG — источники: " + (response.retrievedSources().isEmpty()
                ? "—" : String.join(", ", response.retrievedSources())));
        System.out.println("С RAG — ответ:");
        System.out.println(response.rag().answer());
        System.out.println("БЕЗ RAG — ответ (источники отсутствуют):");
        System.out.println(response.plain().answer());
        System.out.println("Вердикт: " + response.verdict());
    }

    private void printQuestions(List<Day22ControlQuestion> questions) {
        System.out.println("=== КОНТРОЛЬНЫЕ ВОПРОСЫ (" + questions.size() + ") ===");
        for (Day22ControlQuestion question : questions) {
            System.out.println(question.id() + " | " + question.question());
            System.out.println("   ожидание (ключевые слова): "
                    + String.join(", ", question.expectedKeywords()));
            System.out.println("   источники: "
                    + String.join(", ", question.expectedSources()));
        }
    }

    private void printEvaluate(Day22EvalResponse evaluate) {
        System.out.println("=== КАЧЕСТВО НА " + evaluate.total() + " КОНТРОЛЬНЫХ ВОПРОСАХ ===");
        System.out.println("источники найдены (recall): " + evaluate.retrievalHits()
                + "/" + evaluate.total() + " = "
                + String.format(Locale.ROOT, "%.1f", evaluate.retrievalRecallPercent()) + "%");
        System.out.println("среднее покрытие ключевых слов: с RAG "
                + String.format(Locale.ROOT, "%.1f", evaluate.ragAvgCoveragePercent())
                + "% | без RAG " + String.format(Locale.ROOT, "%.1f", evaluate.plainAvgCoveragePercent())
                + "% | разница " + String.format(Locale.ROOT, "%.1f", evaluate.avgCoverageGapPercent()) + " п.п.");
        System.out.println("Вердикт: " + evaluate.verdict());
        for (Day22EvalItem item : evaluate.items()) {
            System.out.println("- " + item.id() + " | " + item.question());
            System.out.println("  источники: " + (item.retrievedSources().isEmpty()
                    ? "—" : String.join(", ", item.retrievedSources()))
                    + (item.retrievalHit() ? " (попадание)" : " (промах)"));
            System.out.println("  покрытие: RAG " + String.format(Locale.ROOT, "%.1f",
                    item.ragCoveragePercent()) + "% | без RAG "
                    + String.format(Locale.ROOT, "%.1f", item.plainCoveragePercent()) + "%");
        }
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