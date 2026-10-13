package com.yunovan.aiadvent.day32;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day32CliRunner implements ApplicationRunner {

    private final Day32ReviewService service;
    private final ApplicationContext applicationContext;

    public Day32CliRunner(Day32ReviewService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"32".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printHealth(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("review")) {
                Day32ReviewResponse response = service.review(request(args));
                printReview(response);
                writeOut(args, response);
                maybeExit(args);
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day32GitException ex) {
            System.out.println("ОШИБКА git: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Подсказка по веб-интерфейсу: http://localhost:8080/day32.html");
        System.out.println("Возможности: --check | --review [--base=<ref>] [--head=<ref>] "
                + "[--diff-file=<path>] [--title=<t>] [--body=<b>] [--out=<path>]");
        System.out.println("Заголовок и описание PR можно передать через DAY32_PR_TITLE и DAY32_PR_BODY.");
        System.out.println("Пример: --day=32 --review --base=main --out=ai-review.md --cli");
    }

    private Day32ReviewRequest request(ApplicationArguments args) {
        String title = option(args, "title", "DAY32_PR_TITLE");
        String body = option(args, "body", "DAY32_PR_BODY");
        String diffFile = firstOption(args, "diff-file");
        String diff = null;
        if (diffFile != null) {
            Path path = Path.of(diffFile);
            if (!Files.isRegularFile(path)) {
                throw new IllegalArgumentException("файл с diff не найден: " + path.toAbsolutePath());
            }
            try {
                diff = Files.readString(path, StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new IllegalArgumentException("не удалось прочитать diff: " + ex.getMessage(), ex);
            }
        }
        return new Day32ReviewRequest(firstOption(args, "base"), firstOption(args, "head"),
                diff, title, body);
    }

    private void writeOut(ApplicationArguments args, Day32ReviewResponse response) {
        String out = firstOption(args, "out");
        if (out == null || out.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(out);
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, response.review(), StandardCharsets.UTF_8);
            System.out.println("Ревью сохранено в " + path.toAbsolutePath());
        } catch (IOException ex) {
            throw new IllegalArgumentException("не удалось записать результат: " + ex.getMessage(), ex);
        }
    }

    void printHealth(Day32HealthResponse health) {
        System.out.println("=== Автоматическое ревью кода (день 32) ===");
        System.out.println("Статус: " + health.status());
        System.out.println("Репозиторий: " + health.repositoryDir() + " (ветка " + health.branch() + ")");
        System.out.println("Базовая ветка для сравнения: " + health.baseRef());
        System.out.println("Индекс проекта: документация + код, чанков " + health.chunks()
                + ", документов " + health.documents() + " (из них кода " + health.codeFiles()
                + "), символов " + health.corpusChars());
        Day32ModelStatus model = health.model();
        System.out.println("Локальная LLM: " + model.endpoint() + " / " + model.model());
        if (model.available()) {
            System.out.println("Ollama: да (версия " + model.version() + ") | модель: "
                    + (model.installed() ? "установлена" : "НЕ установлена"));
        } else {
            System.out.println("Ollama: нет — " + model.reason() + " (ревью соберётся эвристиками)");
        }
    }

    void printReview(Day32ReviewResponse review) {
        System.out.println("=== Ревью pull request ===");
        System.out.println("Диапазон: " + review.base() + "..." + review.head()
                + (review.title().isEmpty() ? "" : " | «" + review.title() + "»"));
        System.out.println("Файлов: " + review.filesChanged() + " | +" + review.addedLines()
                + "/-" + review.removedLines() + " строк | diff " + review.diffChars() + " символов"
                + (review.diffTruncated() ? " (обрезан)" : ""));
        System.out.println(review.summary());
        System.out.println("Найдено фрагментов контекста: " + review.retrieved()
                + (review.sources().isEmpty() ? "" : " — источники: " + String.join(", ", review.sources())));
        System.out.println();
        System.out.println(review.review());
        if (review.fallback()) {
            System.out.println();
            System.out.println("(LLM недоступна: " + review.note() + ")");
        }
    }

    private void maybeExit(ApplicationArguments args) {
        if (args.containsOption("cli")) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private static String option(ApplicationArguments args, String option, String environment) {
        String value = firstOption(args, option);
        if (value != null && !value.isBlank()) {
            return value;
        }
        String fromEnvironment = System.getenv(environment);
        return fromEnvironment == null || fromEnvironment.isBlank() ? null : fromEnvironment;
    }

    private static String firstOption(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
