package com.yunovan.aiadvent.day24;

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
@EnableConfigurationProperties(Day24Properties.class)
public class Day24CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day24CliRunner.class);

    private final Day24Service service;
    private final Day24Properties properties;
    private final ApplicationContext applicationContext;

    public Day24CliRunner(Day24Service service, Day24Properties properties,
                          ApplicationContext applicationContext) {
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"24".equals(firstOption(args, "day"))) {
            log.info("Day 24 web UI: http://localhost:8080/day24.html  |  API: /api/day24/health, "
                    + "/api/day24/questions, /api/day24/answer, /api/day24/evaluate");
            return;
        }

        try {
            if (args.containsOption("check")) {
                printCheck(service.health());
                maybeExit(args);
                return;
            }

            String answer = firstOption(args, "answer");
            if (answer != null && !answer.isBlank()) {
                printAnswer(service.answer(answer));
                maybeExit(args);
                return;
            }

            if (args.containsOption("questions")) {
                printQuestions(service.questions());
                maybeExit(args);
                return;
            }

            if (args.containsOption("unknown")) {
                printAnswer(service.answer(Day24ControlQuestions.WEAK.getFirst().question()));
                maybeExit(args);
                return;
            }

            if (args.containsOption("evaluate")) {
                printEvaluate(service.evaluate());
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

        System.out.println("Цитаты, источники и анти-галлюцинации доступны на http://localhost:8080/day24.html");
        System.out.println("Проверка: --check | Ответ с источниками и цитатами: --answer=<вопрос>");
        System.out.println("Контрольные вопросы: --questions | Комплаентность: --evaluate");
        System.out.println("Демо «не знаю»: --unknown");
    }

    private void printCheck(Day24HealthResponse health) {
        System.out.println("=== ЦИТАТЫ, ИСТОЧНИКИ И АНТИ-ГАЛЛЮЦИНАЦИИ ===");
        System.out.println("документов в корпусе: " + health.documents());
        System.out.println("объём: " + health.corpusChars() + " символов ≈ "
                + health.pagesEstimate() + " страниц");
        System.out.println("стратегия поиска: " + health.strategy());
        System.out.println("топ-K до/после: " + health.topKBefore() + "/" + health.topKAfter());
        System.out.println("порог фильтра: " + String.format(Locale.ROOT, "%.2f", health.threshold())
                + " | порог «не знаю»: " + String.format(Locale.ROOT, "%.2f",
                health.unknownThreshold()));
        System.out.println("query rewrite: " + (health.rewrite() ? "вкл" : "выкл"));
        System.out.println("цитат на источник: " + health.quotesPerSource()
                + ", мин. длина цитаты: " + health.quoteMinChars() + " символов");
        System.out.println("поддержка ответа цитатами ≥ "
                + String.format(Locale.ROOT, "%.0f", health.supportThreshold() * 100) + "%");
        System.out.println("слабые вопросы для проверки «не знаю»: "
                + String.join(" | ", health.weakQuestions()));
        System.out.println("стратегии: " + String.join(", ", health.strategies()));
    }

    private void printAnswer(Day24GroundedResponse response) {
        System.out.println("=== ОТВЕТ С ИСТОЧНИКАМИ И ЦИТАТАМИ ===");
        System.out.println("вопрос: " + response.question());
        System.out.println("запрос для поиска: " + response.matchedQuery()
                + (response.rewritten() ? " (переписан)" : ""));
        System.out.println("кандидатов до фильтрации: " + response.candidatesBefore()
                + " | отсеяно: " + response.filteredOut());
        System.out.println("лучший score: " + String.format(Locale.ROOT, "%.3f",
                response.bestScore()) + (response.unknown() ? " — НИЖЕ порога «не знаю»" : ""));
        System.out.println("источники: " + (response.sources().isEmpty() ? "—"
                : response.sources().size() + " шт."));
        for (Day24Source source : response.sources()) {
            System.out.println("  - " + source.source() + " | раздел «" + source.section()
                    + "» | чанк " + source.chunkId() + " | score "
                    + String.format(Locale.ROOT, "%.3f", source.score()));
        }
        System.out.println("цитаты: " + (response.quotes().isEmpty() ? "—"
                : response.quotes().size() + " шт."));
        for (Day24Quote quote : response.quotes()) {
            System.out.println("  * «" + quote.text() + "»");
            System.out.println("    [" + quote.source() + " | «" + quote.section()
                    + "» | " + quote.chunkId() + " | совпадений: " + quote.matchedKeywords() + "]");
        }
        System.out.println("поддержка ответа цитатами: " + String.format(Locale.ROOT, "%.1f",
                response.supportCoveragePercent()) + "% | подтверждён: "
                + (response.supported() ? "да" : "нет"));
        System.out.println("режим «не знаю»: " + (response.unknown() ? "ДА" : "нет")
                + " | LLM: " + (response.fallback() ? "недоступен, ответ собран из цитат"
                : "отвечал"));
        System.out.println("ответ:");
        System.out.println(response.answer());
    }

    private void printQuestions(List<Day24ControlQuestion> questions) {
        System.out.println("=== КОНТРОЛЬНЫЕ ВОПРОСЫ (" + questions.size() + ") ===");
        for (Day24ControlQuestion question : questions) {
            System.out.println(question.id() + " | " + question.question());
            System.out.println("   ожидание (ключевые слова): "
                    + String.join(", ", question.expectedKeywords()));
            System.out.println("   источники: " + String.join(", ", question.expectedSources()));
        }
    }

    private void printEvaluate(Day24EvalResponse evaluate) {
        System.out.println("=== КОМПЛАЕНТНОСТЬ НА " + evaluate.knownCount()
                + " ВОПРОСАХ + АНТИ-ГАЛЛЮЦИНАЦИЯ ===");
        System.out.println("источники в каждом ответе: " + evaluate.sourcesPresent() + "/"
                + evaluate.knownCount());
        System.out.println("цитаты в каждом ответе: " + evaluate.quotesPresent() + "/"
                + evaluate.knownCount());
        System.out.println("смысл ответа подтверждён цитатами: " + evaluate.supportedCount() + "/"
                + evaluate.knownCount() + " (средняя поддержка "
                + String.format(Locale.ROOT, "%.1f", evaluate.avgSupportPercent()) + "%)");
        System.out.println("режим «не знаю» на слабых вопросах: " + evaluate.unknownTriggered() + "/"
                + evaluate.weakCount());
        System.out.println("Вердикт: " + evaluate.verdict());
        for (Day24EvalItem item : evaluate.questions()) {
            System.out.println("- " + item.id() + " | " + item.question());
            System.out.println("  источники: " + (item.hasSources() ? "да (" + item.sourcesCount()
                    + ")" : "НЕТ") + " | цитаты: " + (item.hasQuotes() ? "да (" + item.quotesCount()
                    + ")" : "НЕТ") + " | подтверждён: " + (item.supported() ? "да" : "нет")
                    + " (" + String.format(Locale.ROOT, "%.0f", item.supportCoveragePercent())
                    + "%)" + (item.unknown() ? " | «не знаю»: ДА" : ""));
        }
        for (Day24EvalWeakItem weak : evaluate.weak()) {
            System.out.println("- " + weak.id() + " | " + weak.question()
                    + " → «не знаю»: " + (weak.unknown() ? "да" : "нет")
                    + " | score " + String.format(Locale.ROOT, "%.3f", weak.bestScore()));
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