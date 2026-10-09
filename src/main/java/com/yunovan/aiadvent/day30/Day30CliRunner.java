package com.yunovan.aiadvent.day30;

import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day29.Day29ModelReport;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day30CliRunner implements ApplicationRunner {

    private final Day30PrivateLlmService service;
    private final ApplicationContext applicationContext;

    public Day30CliRunner(Day30PrivateLlmService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"30".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printHealth(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("chat")) {
                printChat(service.chat(
                        new Day30ChatRequest("cli", firstOption(args, "chat")), "cli", null));
                maybeExit(args);
                return;
            }

            if (args.containsOption("stress")) {
                printStress(service.stress(null, "cli", null));
                maybeExit(args);
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day30AuthException | Day30RateLimitException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day26LlmException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Подсказка по веб-интерфейсу: http://localhost:8080/day30.html");
        System.out.println("Состояние: --check | чат: --chat=\"<сообщение>\" | "
                + "стабильность: --stress");
        System.out.println("Пример: --day=30 --chat=\"Привет!\" --cli");
    }

    void printHealth(Day30HealthResponse health) {
        System.out.println("=== Приватный AI-сервис на локальной LLM (день 30) ===");
        System.out.println("Статус: " + health.status());
        System.out.println("Сервис: " + health.serviceUrl());
        if (health.networkUrls().size() > 1) {
            System.out.println("Доступ по сети: "
                    + String.join(", ", health.networkUrls().subList(1, health.networkUrls().size())));
        }
        Day29ModelReport model = health.model();
        System.out.println("Модель: " + model.endpoint() + " / " + model.model());
        if (model.available()) {
            System.out.println("Сервер: да (версия " + model.version() + ") | модель: "
                    + (model.installed() ? "установлена" : "НЕ установлена"));
            if (!model.quantizationLevel().isEmpty()) {
                System.out.println("Квантование: " + model.quantizationLevel()
                        + " | параметры: " + model.parameterSize()
                        + " | контекстное окно: " + model.contextLength());
            }
        } else {
            System.out.println("Сервер: нет");
            System.out.println("Причина: " + model.reason());
        }
        Day30Limits limits = health.limits();
        System.out.println("Ограничения: сообщений " + limits.maxMessages()
                + " | контекст " + limits.maxPromptChars() + " символов"
                + " | rate limit " + limits.rateLimitPerMinute() + "/мин"
                + " | одновременно " + limits.maxConcurrent()
                + " | max_tokens " + limits.maxOutputTokens());
        System.out.println("Ключ доступа: "
                + (limits.apiKeyRequired() ? "требуется (X-Api-Key)" : "не требуется"));
        Day30Stats stats = health.stats();
        StringBuilder line = new StringBuilder("Статистика: запросов " + stats.totalRequests()
                + " | принято " + stats.acceptedRequests()
                + " | отклонено (лимит " + stats.rejectedByRateLimit()
                + ", контекст " + stats.rejectedByContext()
                + ", занятость " + stats.rejectedByConcurrency() + ")"
                + " | активно " + stats.activeRequests()
                + " (пик " + stats.peakActiveRequests() + ")"
                + " | сессий " + stats.sessions());
        if (stats.avgLatencyMs() != null) {
            line.append(" | средняя задержка ")
                    .append(String.format(Locale.ROOT, "%.0f", stats.avgLatencyMs())).append(" мс");
        }
        System.out.println(line);
    }

    void printChat(Day30ChatResponse chat) {
        System.out.println("=== Чат приватного сервиса ===");
        System.out.println("Сессия: " + chat.sessionId() + " | ход: " + chat.turn()
                + " | сообщений в контексте: " + chat.contextMessages()
                + (chat.contextTrimmed() ? " (история обрезана по лимиту)" : ""));
        System.out.println("Ответ: " + chat.reply());
        System.out.println("Задержка: " + chat.latencyMs() + " мс | токены "
                + chat.promptTokens() + " вход / " + chat.outputTokens() + " выход | "
                + String.format(Locale.ROOT, "%.1f", chat.tokensPerSecond()) + " ток/с"
                + " | остаток лимита: " + chat.rateRemaining() + " запросов в минуту");
    }

    void printStress(Day30StressResponse stress) {
        System.out.println("=== Проверка стабильности под нагрузкой ===");
        System.out.println("Запросов: " + stress.requests() + " | одновременно: "
                + stress.concurrency() + " | всего времени: " + stress.totalMs() + " мс");
        System.out.println("Успешно: " + stress.succeeded()
                + " | отклонено лимитом: " + stress.rateLimited()
                + " | ошибок: " + stress.failed());
        if (stress.succeeded() > 0) {
            System.out.println("Задержки: мин " + stress.minLatencyMs()
                    + " / сред " + String.format(Locale.ROOT, "%.0f", stress.avgLatencyMs())
                    + " / макс " + stress.maxLatencyMs() + " мс"
                    + " | пропускная способность "
                    + String.format(Locale.ROOT, "%.1f", stress.throughputPerSecond()) + " зап/с");
        }
        for (Day30StressItem item : stress.items()) {
            System.out.println("#" + item.index() + " "
                    + (item.ok() ? "ok" : item.rateLimited() ? "лимит" : "ошибка")
                    + " " + item.latencyMs() + " мс"
                    + (item.error().isEmpty() ? "" : " — " + item.error()));
        }
        System.out.println("Вердикт: " + stress.verdict());
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
