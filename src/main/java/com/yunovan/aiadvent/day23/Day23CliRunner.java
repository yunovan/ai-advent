package com.yunovan.aiadvent.day23;

import com.yunovan.aiadvent.day21.Day21IndexException;
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
@EnableConfigurationProperties(Day23Properties.class)
public class Day23CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day23CliRunner.class);

    private final Day23RagService service;
    private final Day23Properties properties;
    private final ApplicationContext applicationContext;

    public Day23CliRunner(Day23RagService service, Day23Properties properties,
                          ApplicationContext applicationContext) {
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"23".equals(firstOption(args, "day"))) {
            log.info("Day 23 web UI: http://localhost:8080/day23.html  |  API: /api/day23/health, "
                    + "/api/day23/questions, /api/day23/rewrite, /api/day23/answer, "
                    + "/api/day23/compare, /api/day23/evaluate");
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day23HealthResponse health = service.health();
                System.out.println("=== RAG / РЕРАНКИНГ И ФИЛЬТРАЦИЯ ===");
                System.out.println("документов в корпусе: " + health.documents());
                System.out.println("объём: " + health.corpusChars() + " символов ≈ "
                        + health.pagesEstimate() + " страниц");
                System.out.println("стратегия поиска: " + health.strategy());
                System.out.println("топ-K до фильтрации: " + health.topKBefore()
                        + " | после: " + health.topKAfter());
                System.out.println("порог отсечения score: " + String.format(Locale.ROOT, "%.2f",
                        health.threshold()));
                System.out.println("query rewrite включён: " + (health.rewrite() ? "да" : "нет")
                        + " | ответ до " + health.answerMaxTokens() + " токенов");
                maybeExit(args);
                return;
            }

            String rewrite = firstOption(args, "rewrite");
            if (rewrite != null && !rewrite.isBlank()) {
                printRewrite(service.rewrite(rewrite));
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
                printCompare(service.compare(compare, null, null));
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

        System.out.println("Реранкинг и фильтрация доступны на http://localhost:8080/day23.html");
        System.out.println("Проверка: --check | Переписывание запроса: --rewrite=<вопрос>");
        System.out.println("Ответ: --answer=<вопрос> [--mode=base|filter|rewrite|full]");
        System.out.println("Сравнение режимов: --compare=<вопрос> | Контрольные вопросы: --questions");
        System.out.println("Качество на 10 вопросах: --evaluate | Агент: --prompt=<вопрос>");
    }

    private void printRewrite(Day23RewriteResponse response) {
        System.out.println("=== ПЕРЕПИСЫВАНИЕ ЗАПРОСА ===");
        System.out.println("исходный запрос: " + response.original());
        System.out.println("переписанный: " + response.rewritten());
        System.out.println("применено правил: " + response.expansions().size());
        for (String expansion : response.expansions()) {
            System.out.println("  + «" + expansion + "»");
        }
    }

    private void printAnswer(Day23AnswerResponse response) {
        System.out.println("=== ОТВЕТ АГЕНТА (РЕЖИМ " + response.mode().toUpperCase(Locale.ROOT) + ") ===");
        System.out.println("вопрос: " + response.question());
        System.out.println("режим: " + response.mode());
        System.out.println("запрос для поиска: " + response.matchedQuery()
                + (response.rewritten() ? " (переписан)" : ""));
        System.out.println("кандидатов до фильтрации: " + response.candidatesBefore()
                + " | отсеяно: " + response.filteredOut());
        List<String> sources = response.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName()).distinct().toList();
        System.out.println("источники: " + (sources.isEmpty() ? "—"
                : String.join(", ", sources)));
        System.out.println("fallback (LLM недоступен): " + (response.fallback() ? "да" : "нет"));
        System.out.println("ответ:");
        System.out.println(response.answer());
    }

    private void printCompare(Day23CompareResponse response) {
        System.out.println("=== СРАВНЕНИЕ РЕЖИМОВ: " + response.first().mode().toUpperCase(Locale.ROOT)
                + " / " + response.second().mode().toUpperCase(Locale.ROOT) + " ===");
        System.out.println("вопрос: " + response.question());
        System.out.println(response.first().mode().toUpperCase(Locale.ROOT)
                + " — кандидатов: " + response.first().candidatesBefore()
                + ", отсеяно: " + response.first().filteredOut()
                + ", источники: " + (response.sourcesBefore().isEmpty() ? "—"
                : String.join(", ", response.sourcesBefore())));
        System.out.println("источники после: " + (response.sourcesAfter().isEmpty() ? "—"
                : String.join(", ", response.sourcesAfter())));
        System.out.println("ответ «" + response.first().mode() + "»:");
        System.out.println(response.first().answer());
        System.out.println("ответ «" + response.second().mode() + "»:");
        System.out.println(response.second().answer());
        System.out.println("Вердикт: " + response.verdict());
    }

    private void printQuestions(List<Day23ControlQuestion> questions) {
        System.out.println("=== КОНТРОЛЬНЫЕ ВОПРОСЫ (" + questions.size() + ") ===");
        for (Day23ControlQuestion question : questions) {
            System.out.println(question.id() + " | " + question.question());
            System.out.println("   ожидание (ключевые слова): "
                    + String.join(", ", question.expectedKeywords()));
            System.out.println("   источники: "
                    + String.join(", ", question.expectedSources()));
        }
    }

    private void printEvaluate(Day23EvalResponse evaluate) {
        System.out.println("=== КАЧЕСТВО НА " + evaluate.total() + " КОНТРОЛЬНЫХ ВОПРОСАХ ===");
        System.out.println("recall источников: base " + evaluate.baseRetrievalHits() + "/"
                + evaluate.total() + " = " + String.format(Locale.ROOT, "%.1f",
                evaluate.baseRecallPercent()) + "% | с фильтром+rewrite "
                + evaluate.fullRetrievalHits() + "/" + evaluate.total() + " = "
                + String.format(Locale.ROOT, "%.1f", evaluate.fullRecallPercent()) + "%");
        System.out.println("отсеяно кандидатов фильтром всего: " + evaluate.totalFilteredOut());
        System.out.println("среднее покрытие ключевых слов: base "
                + String.format(Locale.ROOT, "%.1f", evaluate.avgCoverageBasePercent())
                + "% | full " + String.format(Locale.ROOT, "%.1f", evaluate.avgCoverageFullPercent())
                + "% | разница " + String.format(Locale.ROOT, "%.1f", evaluate.coverageGapPercent())
                + " п.п.");
        System.out.println("Вердикт: " + evaluate.verdict());
        for (Day23EvalItem item : evaluate.items()) {
            System.out.println("- " + item.id() + " | " + item.question());
            System.out.println("  base: " + (item.baseHit() ? "попадание" : "промах")
                    + ", покрытие " + String.format(Locale.ROOT, "%.1f", item.baseCoveragePercent())
                    + "%; full: " + (item.fullHit() ? "попадание" : "промах")
                    + ", покрытие " + String.format(Locale.ROOT, "%.1f", item.fullCoveragePercent())
                    + "%, отсеяно " + item.filteredOut());
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