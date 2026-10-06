package com.yunovan.aiadvent.day26;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class Day26Service {

    private final Day26LocalLlmClient client;
    private final Day26Properties properties;

    public Day26Service(Day26LocalLlmClient client, Day26Properties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Day26HealthResponse health() {
        String endpoint = properties.endpoint();
        String model = properties.model();
        try {
            String version = client.version();
            List<Day26InstalledModel> models = client.models();
            boolean installed = models.stream().anyMatch(m -> matches(m.name(), model));
            return new Day26HealthResponse(
                    endpoint, model, true, version, installed, models, "");
        } catch (Day26LlmException ex) {
            return new Day26HealthResponse(endpoint, model, false, "", false, List.of(), ex.getMessage());
        } catch (RuntimeException ex) {
            return new Day26HealthResponse(endpoint, model, false, "", false, List.of(),
                    "Не удалось проверить локальный LLM: " + message(ex));
        }
    }

    public List<Day26Task> tasks() {
        return Day26Task.ALL;
    }

    public Day26RunReport run() {
        List<Day26TaskResult> results = new ArrayList<>();
        long started = System.currentTimeMillis();
        for (Day26Task task : Day26Task.ALL) {
            results.add(execute(task));
        }
        long totalLatencyMs = System.currentTimeMillis() - started;
        int ok = (int) results.stream().filter(Day26TaskResult::ok).count();
        int failed = results.size() - ok;
        return new Day26RunReport(
                properties.endpoint(), properties.model(), results, ok, failed,
                totalLatencyMs, verdict(ok, results.size()));
    }

    public Day26Answer ask(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Запрос не может быть пустым");
        }
        return client.chat("", prompt);
    }

    private Day26TaskResult execute(Day26Task task) {
        try {
            return Day26TaskResult.ok(task, client.chat(task.systemPrompt(), task.prompt()));
        } catch (Day26LlmException ex) {
            return Day26TaskResult.failed(task, ex.getMessage());
        } catch (RuntimeException ex) {
            return Day26TaskResult.failed(task, "Ошибка запроса к локальной LLM: " + message(ex));
        }
    }

    private static String verdict(int ok, int total) {
        if (ok == total) {
            return "все " + total + " запросов выполнены: локальная LLM запущена и отвечает";
        }
        if (ok == 0) {
            return "ни один из " + total + " запросов не выполнен: запустите локальный LLM "
                    + "(ollama serve) и проверьте endpoint";
        }
        return ok + " из " + total + " запросов выполнены, остальные завершились ошибкой";
    }

    private static boolean matches(String installed, String expected) {
        if (installed == null || expected == null) {
            return false;
        }
        return installed.trim().toLowerCase(Locale.ROOT)
                .equals(expected.trim().toLowerCase(Locale.ROOT));
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
