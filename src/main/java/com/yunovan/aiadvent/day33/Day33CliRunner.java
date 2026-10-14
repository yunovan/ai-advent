package com.yunovan.aiadvent.day33;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day33CliRunner implements ApplicationRunner {

    private final Day33SupportService service;
    private final ApplicationContext applicationContext;

    public Day33CliRunner(Day33SupportService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"33".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printHealth(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("kb")) {
                printKb(service.kb());
                maybeExit(args);
                return;
            }

            if (args.containsOption("tools")) {
                printTools(service.tools());
                maybeExit(args);
                return;
            }

            if (args.containsOption("users")) {
                printUsers(service.users());
                maybeExit(args);
                return;
            }

            if (args.containsOption("tickets")) {
                printTickets(service.tickets());
                maybeExit(args);
                return;
            }

            String question = firstOption(args, "ask");
            if (question == null) {
                question = firstOption(args, "help");
            }
            if (question != null) {
                printAsk(service.ask(question, firstOption(args, "ticket"), firstOption(args, "user")));
                maybeExit(args);
                return;
            }

            String tool = firstOption(args, "crm");
            if (tool != null) {
                printCrm(service.crm(tool, crmArguments(args)));
                maybeExit(args);
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Ошибка: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day33SupportException | Day33McpException ex) {
            System.out.println("Ошибка: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Откройте веб-интерфейс: http://localhost:8080/day33.html");
        System.out.println("Возможности: --check | --kb | --tools | --users | --tickets | "
                + "--ask=\"<вопрос>\" [--ticket=T-1001] [--user=U-1001] | "
                + "--crm=user|ticket|user_tickets|search_tickets|open_tickets [--id=] [--query=]");
        System.out.println("Пример: --day=33 --ask=\"Почему не работает авторизация?\" "
                + "--ticket=T-1001 --cli");
    }

    void printHealth(Day33HealthResponse health) {
        System.out.println("=== Ассистент поддержки (день 33) ===");
        System.out.println("Статус: " + health.status());
        System.out.println("MCP CRM: " + health.endpoint() + " ("
                + (health.mcp().available() ? "доступен, инструментов " + health.mcp().tools()
                        : "нет - " + health.mcp().reason()) + ")");
        System.out.println("База знаний: " + health.kbDir() + " — " + health.documents().size()
                + " файлов, " + health.chunks() + " чанков, " + health.corpusChars() + " символов");
        if (!health.documents().isEmpty()) {
            System.out.println("Документы: " + String.join(", ", health.documents()));
        }
        System.out.println("CRM: " + health.crmDir() + " — пользователей " + health.users()
                + ", обращений " + health.tickets());
        Day33ModelStatus model = health.model();
        System.out.println("Локальная LLM: " + model.endpoint() + " / " + model.model());
        if (model.available()) {
            System.out.println("Ollama: да (версия " + model.version() + ") | Модель: "
                    + (model.installed() ? "установлена" : "не установлена"));
        } else {
            System.out.println("Ollama: нет - " + model.reason());
        }
    }

    void printKb(List<Day33DocumentInfo> documents) {
        System.out.println("=== База знаний (день 33) ===");
        System.out.println("Файлов: " + documents.size());
        for (Day33DocumentInfo document : documents) {
            System.out.println("- " + document.source() + " («" + document.title() + "»), "
                    + document.chars() + " символов, чанков " + document.chunks());
        }
    }

    void printTools(Day33ToolsResponse tools) {
        System.out.println("=== MCP-инструменты CRM (день 33) ===");
        if (!tools.available()) {
            System.out.println("MCP недоступен: " + tools.reason());
            return;
        }
        System.out.println("Эндпоинт: " + tools.endpoint() + " | инструментов: " + tools.count());
        for (Day33ToolInfo tool : tools.tools()) {
            System.out.println("- " + tool.name() + ": " + tool.description());
        }
    }

    void printUsers(List<Day33UserInfo> users) {
        System.out.println("=== Пользователи CRM (день 33) ===");
        System.out.println("Всего: " + users.size());
        for (Day33UserInfo user : users) {
            System.out.println("- " + user.id() + " " + user.name() + " (" + user.email()
                    + ", тариф " + user.plan() + ", обращений " + user.tickets() + ")");
        }
    }

    void printTickets(List<Day33TicketInfo> tickets) {
        System.out.println("=== Обращения CRM (день 33) ===");
        System.out.println("Всего: " + tickets.size());
        for (Day33TicketInfo ticket : tickets) {
            System.out.println("- " + ticket.id() + " [" + ticket.status() + "/" + ticket.priority()
                    + "] " + ticket.subject() + " — " + ticket.userId() + ", обновлено "
                    + ticket.updatedAt());
        }
    }

    void printCrm(Day33CrmResponse crm) {
        System.out.println("=== Инструмент MCP: " + crm.tool() + " ===");
        System.out.println(crm.content());
    }

    void printAsk(Day33SupportResponse answer) {
        System.out.println("=== Ответ ассистента поддержки ===");
        System.out.println("Вопрос: " + answer.question());
        if (!answer.ticketId().isEmpty()) {
            System.out.println("Обращение: " + answer.ticketId()
                    + (answer.ticketSubject().isEmpty() ? "" : " («" + answer.ticketSubject() + "»)"));
        }
        if (!answer.userId().isEmpty()) {
            System.out.println("Пользователь: " + answer.userId()
                    + (answer.userName().isEmpty() ? "" : " (" + answer.userName() + ")"));
        }
        System.out.println("CRM: " + (answer.mcpUsed()
                ? "использована, инструментов " + answer.mcpTools() : "не использована"));
        System.out.println("База знаний: " + answer.retrieved()
                + (answer.sourceFiles().isEmpty() ? "" : " — источники: "
                        + String.join(", ", answer.sourceFiles())));
        System.out.println("Ответ: " + answer.answer());
        StringBuilder metrics = new StringBuilder("Латентность: " + answer.latencyMs() + " мс"
                + " | токены " + answer.promptTokens() + " вход / " + answer.outputTokens() + " выход"
                + " | " + String.format(Locale.ROOT, "%.1f", answer.tokensPerSecond()) + " ток/с");
        if (answer.groundingPercent() != null) {
            metrics.append(" | опора на источники ")
                    .append(String.format(Locale.ROOT, "%.0f", answer.groundingPercent())).append('%');
        }
        if (answer.fallback()) {
            metrics.append(" | ответ собран из базы знаний (LLM недоступна)");
        }
        System.out.println(metrics);
    }

    private static Map<String, Object> crmArguments(ApplicationArguments args) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        String id = firstOption(args, "id");
        String user = firstOption(args, "user");
        String ticket = firstOption(args, "ticket");
        String query = firstOption(args, "query");
        String limit = firstOption(args, "limit");
        if (user != null) {
            arguments.put("userId", user);
        } else if (id != null) {
            arguments.put("userId", id);
        }
        if (ticket != null) {
            arguments.put("ticketId", ticket);
        } else if (id != null) {
            arguments.put("ticketId", id);
        }
        if (query != null) {
            arguments.put("query", query);
        }
        if (limit != null) {
            arguments.put("limit", limit);
        }
        return arguments;
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
