package com.yunovan.aiadvent.day27;

import com.yunovan.aiadvent.day26.Day26LlmException;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day27CliRunner implements ApplicationRunner {

    static final String CLI_SESSION = "cli";

    private final Day27ChatService service;
    private final ApplicationContext applicationContext;

    public Day27CliRunner(Day27ChatService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"27".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printHealth(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("ask")) {
                printTurn(service.chat(CLI_SESSION, firstOption(args, "ask")));
                maybeExit(args);
                return;
            }

            if (args.containsOption("history")) {
                printHistory(service.history(CLI_SESSION));
                maybeExit(args);
                return;
            }

            if (args.containsOption("reset")) {
                service.reset(CLI_SESSION);
                System.out.println("История сессии " + CLI_SESSION + " очищена.");
                maybeExit(args);
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day26LlmException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Локальный ассистент: http://localhost:8080/day27.html");
        System.out.println("Состояние: --check | Диалог: --ask=\"<текст>\" | История: --history");
        System.out.println("Сброс диалога: --reset");
        System.out.println("Пример: --day=27 --ask=\"Привет! Кто ты?\" --cli");
    }

    void printHealth(Day27HealthResponse health) {
        System.out.println("=== ЛОКАЛЬНЫЙ АССИСТЕНТ ===");
        System.out.println("endpoint: " + health.endpoint());
        System.out.println("модель: " + health.model());
        if (health.available()) {
            System.out.println("сервер запущен: ДА (версия " + health.version() + ")");
            System.out.println("модель установлена: " + (health.modelInstalled() ? "ДА" : "НЕТ"));
        } else {
            System.out.println("сервер запущен: НЕТ");
            System.out.println("причина: " + health.error());
        }
        System.out.println("облачные модели: НЕТ (только локальный Ollama)");
        System.out.println("история: до " + health.historyLimit() + " сообщений | сессий: "
                + health.sessions() + " (максимум " + health.maxSessions() + ")");
    }

    void printTurn(Day27ChatTurn turn) {
        System.out.println("=== ДИАЛОГ · сессия " + turn.sessionId() + ", ход " + turn.turn() + " ===");
        System.out.println("вы: " + turn.userMessage());
        System.out.println("ассистент: " + turn.reply());
        System.out.println("задержка: " + turn.latencyMs() + " мс | токены: "
                + turn.promptTokens() + " вход / " + turn.outputTokens()
                + " выход | скорость: " + String.format(Locale.ROOT, "%.1f", turn.tokensPerSecond())
                + " ток/с | модель: " + turn.model());
    }

    void printHistory(List<Day27Message> history) {
        if (history.isEmpty()) {
            System.out.println("История диалога пуста.");
            return;
        }
        System.out.println("=== ИСТОРИЯ ДИАЛОГА (" + history.size() + " сообщений) ===");
        for (Day27Message message : history) {
            if ("user".equals(message.role())) {
                System.out.println("[" + message.turn() + "] пользователь: " + message.text());
            } else {
                System.out.println("[" + message.turn() + "] ассистент: " + message.text()
                        + " (" + message.latencyMs() + " мс, " + message.promptTokens() + "+"
                        + message.outputTokens() + " токенов)");
            }
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
