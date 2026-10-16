package com.yunovan.aiadvent.day34;

import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day34FileAgent {

    private static final Logger log = LoggerFactory.getLogger(Day34FileAgent.class);

    private static final int MAX_CONTEXT_CHARS = 14_000;
    private static final int MAX_EXCERPT_FILES = 4;
    private static final int MAX_INVARIANT_FILES = 40;

    private static final String SYSTEM_PROMPT =
            "Ты — инженерный ассистент, который работает с файлами проекта. "
                    + "Ты получаешь задачу и уже прочитанные фрагменты кода и документации. "
                    + "Отвечай на русском языке, только markdown, без вымышленных фактов: "
                    + "опирайся исключительно на предоставленный контекст.";

    private final Day34Properties properties;
    private final Day34Workspace workspace;
    private final Day34McpClient client;
    private final Day34GoalPlanner planner;
    private final Day26LocalLlmClient llm;

    public Day34FileAgent(Day34Properties properties, Day34Workspace workspace,
            Day34McpClient client, Day34GoalPlanner planner, Day26LocalLlmClient llm) {
        this.properties = properties;
        this.workspace = workspace;
        this.client = client;
        this.planner = planner;
        this.llm = llm;
    }

    public Day34RunResponse run(Day34RunRequest request) {
        long started = System.nanoTime();
        String goal = request.goal() == null ? "" : request.goal().trim();
        if (goal.isBlank() && (request.task() == null || request.task().isBlank())) {
            throw new Day34Exception("Не указана цель: задайте goal или task");
        }
        Day34TaskType task = planner.classify(goal, request.task());
        String target = planner.extractTarget(goal, request.target());

        Session session = new Session();
        session.ops = openOps(session);
        session.evidence.add("Подключение: " + session.ops.kind());

        Summary summary = switch (task) {
            case FIND_USAGES -> findUsages(session, goal, target);
            case GENERATE_ADR, GENERATE_CHANGELOG, GENERATE_README -> generateDocument(session, task, goal, target);
            case CHECK_INVARIANTS -> checkInvariants(session, goal);
            case UPDATE_DOC -> updateDoc(session, goal, target);
        };

        persist(session, request.write());

        long latencyMs = Math.max(0, (System.nanoTime() - started) / 1_000_000L);
        String diff = session.artifacts.stream()
                .map(Day34Artifact::diff)
                .filter(value -> value != null && !value.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
        String note = "Файловые операции: " + session.ops.kind()
                + (Boolean.TRUE.equals(request.write()) ? ", режим записи" : ", режим предпросмотра (diff)");
        return new Day34RunResponse(
                task.code(),
                task.label(),
                goal,
                target,
                List.copyOf(session.plan),
                List.copyOf(session.steps),
                List.copyOf(session.evidence),
                session.files.size(),
                List.copyOf(session.files),
                session.matches,
                List.copyOf(session.findings),
                List.copyOf(session.artifacts),
                diff,
                List.copyOf(session.written),
                summary.text(),
                summary.markdown(),
                summary.fallback(),
                session.llmModel,
                latencyMs,
                session.promptTokens,
                session.outputTokens,
                session.tokensPerSecond,
                note);
    }

    private Ops openOps(Session session) {
        try {
            Day34Connection connection = client.connect();
            List<Day34ToolInfo> tools = client.listTools(connection);
            session.evidence.add("MCP-инструменты: " + tools.size() + " (" + toolNames(tools) + ")");
            return new McpOps(client, connection);
        } catch (RuntimeException ex) {
            log.warn("День 34: MCP недоступен ({}), использую локальный доступ к файлам", ex.getMessage());
            session.evidence.add("MCP недоступен: " + ex.getMessage() + " — переход в локальный режим");
            return new LocalOps(workspace);
        }
    }

    private Summary findUsages(Session session, String goal, String target) {
        String subject = target == null || target.isBlank() ? "Day" : target;
        List<Day34SearchMatch> matches = search(session, subject, globFor(subject), "найти использования");
        session.matches = matches.size();
        session.evidence.add("Совпадений по «" + subject + "»: " + matches.size());

        Map<String, List<Day34SearchMatch>> byFile = new LinkedHashMap<>();
        for (Day34SearchMatch match : matches) {
            byFile.computeIfAbsent(match.path(), key -> new ArrayList<>()).add(match);
        }
        int excerpted = 0;
        for (String path : byFile.keySet()) {
            if (excerpted >= MAX_EXCERPT_FILES) {
                break;
            }
            read(session, path, null, MAX_CONTEXT_CHARS / MAX_EXCERPT_FILES, "прочитать файл с использованием");
            excerpted++;
        }

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Использования «").append(subject).append("»\n\n");
        markdown.append("Найдено ").append(matches.size()).append(" совпадений в ")
                .append(byFile.size()).append(" файлах.\n\n");
        if (matches.isEmpty()) {
            markdown.append("Совпадений не найдено — проверьте название цели.\n");
        } else {
            for (Map.Entry<String, List<Day34SearchMatch>> entry : byFile.entrySet()) {
                markdown.append("## `").append(entry.getKey()).append("`\n\n");
                for (Day34SearchMatch match : entry.getValue()) {
                    markdown.append("- строка ").append(match.line()).append(": `")
                            .append(match.text()).append("`\n");
                }
                markdown.append('\n');
            }
        }
        String analysis = ask(session, "Цель: " + goal + "\nНайденные совпадения и фрагменты:\n"
                + context(session));
        boolean fallback = analysis == null;
        if (!fallback) {
            markdown.append("## Анализ\n\n").append(analysis).append('\n');
        }
        String path = properties.outputDir() + "/usages-" + slug(subject) + ".md";
        addDocumentArtifact(session, path, markdown.toString(), !fallback);
        String text = "Найдено " + matches.size() + " использований «" + subject + "» в "
                + byFile.size() + " файлах";
        return new Summary(text, markdown.toString(), fallback);
    }

    private Summary generateDocument(Session session, Day34TaskType task, String goal, String target) {
        String path = documentPath(task, target);
        String existing = safeRead(path);
        listInto(session, properties.codeDir(), "обзор исходников");
        if (task == Day34TaskType.GENERATE_README) {
            String readme = safeRead(properties.readmePath());
            if (readme != null) {
                session.evidence.add("Прочитан существующий " + properties.readmePath());
                session.files.add(properties.readmePath());
                addContext(session, properties.readmePath(), readme);
            }
        } else if (task == Day34TaskType.GENERATE_CHANGELOG) {
            listInto(session, properties.docsDir(), "обзор документации");
        } else {
            List<Day34SearchMatch> matches = search(session, target == null ? "day" : target,
                    "*.java", "собрать материал по теме");
            Set<String> sources = new LinkedHashSet<>();
            for (Day34SearchMatch match : matches) {
                if (sources.size() >= MAX_EXCERPT_FILES) {
                    break;
                }
                sources.add(match.path());
            }
            for (String source : sources) {
                read(session, source, null, MAX_CONTEXT_CHARS / MAX_EXCERPT_FILES, "прочитать источник");
            }
        }
        String prompt = "Цель: " + goal + "\nЗадача: " + task.label()
                + "\nСформируй итоговый документ в markdown.\n\nКонтекст проекта:\n" + context(session);
        String generated = ask(session, prompt);
        boolean fallback = generated == null;
        String markdown = fallback ? fallbackDocument(task, goal, target, session) : generated;
        addDocumentArtifact(session, path, markdown, !fallback);
        String text = task.label() + ": " + path + (fallback ? " (без модели)" : "");
        return new Summary(text, markdown, fallback);
    }

    private Summary updateDoc(Session session, String goal, String target) {
        String path = target != null && looksLikePath(target) ? target : properties.readmePath();
        String existing = safeRead(path);
        if (existing == null) {
            throw new Day34Exception("Документ для обновления не найден: " + path);
        }
        session.files.add(path);
        session.evidence.add("Прочитан " + path + " (" + lineCount(existing) + " строк)");
        listInto(session, properties.codeDir(), "обзор исходников");
        String prompt = "Цель: " + goal + "\nОбнови документ `" + path + "`, сохранив полезную часть "
                + "и дополнив её по актуальному коду. Верни полный новый markdown.\n\n"
                + "Текущий документ:\n" + truncate(existing)
                + "\n\nФрагменты кода:\n" + context(session);
        String generated = ask(session, prompt);
        boolean fallback = generated == null;
        String markdown = fallback ? existing + "\n\n## Обновление\n\n" + contextSummary(session) : generated;
        String diff = Day34DiffUtil.unified(path, existing, markdown);
        int bytes = markdown.getBytes(StandardCharsets.UTF_8).length;
        session.artifacts.add(new Day34Artifact(path, "update", markdown, bytes, diff));
        String text = "Обновление документа " + path + (fallback ? " (без модели)" : "");
        return new Summary(text, markdown, fallback);
    }

    private Summary checkInvariants(Session session, String goal) {
        List<Day34FileInfo> files = list(session, properties.codeDir(), "список файлов для проверки");
        Map<String, String> contents = new LinkedHashMap<>();
        int read = 0;
        for (Day34FileInfo file : files) {
            if (read >= MAX_INVARIANT_FILES) {
                session.evidence.add("Ограничение анализа: первые " + MAX_INVARIANT_FILES + " файлов");
                break;
            }
            String content = read(session, file.path(), null, properties.maxFileBytes() / 4,
                    "прочитать для проверки");
            if (content != null) {
                contents.put(file.path(), content);
                read++;
            }
        }
        session.findings.addAll(Day34InvariantRules.analyze(contents));
        session.findings.sort(Comparator.comparingInt(f -> severityRank(f.severity())));
        Map<String, Integer> byRule = new LinkedHashMap<>();
        for (Day34Finding finding : session.findings) {
            byRule.merge(finding.rule(), 1, Integer::sum);
        }
        long high = session.findings.stream().filter(f -> "high".equals(f.severity())).count();
        long medium = session.findings.stream().filter(f -> "medium".equals(f.severity())).count();
        long low = session.findings.stream().filter(f -> "low".equals(f.severity())).count();

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Отчёт о проверке инвариантов\n\n");
        markdown.append("Проверено файлов: ").append(contents.size()).append(". Найдено замечаний: ")
                .append(session.findings.size())
                .append(" (high: ").append(high).append(", medium: ").append(medium)
                .append(", low: ").append(low).append(").\n\n");
        if (!byRule.isEmpty()) {
            markdown.append("## По правилам\n\n");
            byRule.forEach((rule, count) -> markdown.append("- `").append(rule).append("`: ")
                    .append(count).append('\n'));
            markdown.append('\n');
        }
        if (session.findings.isEmpty()) {
            markdown.append("Замечаний не обнаружено.\n");
        } else {
            markdown.append("## Замечания\n\n");
            for (Day34Finding finding : session.findings) {
                markdown.append("- [").append(finding.severity()).append("] `").append(finding.rule())
                        .append("` — ").append(finding.file()).append(':').append(finding.line())
                        .append(" — ").append(finding.message()).append('\n');
            }
        }
        String recommendations = ask(session, "Цель: " + goal + "\nЗамечания по инвариантам:\n"
                + markdown + "\nКратко расставь приоритеты исправления.");
        boolean fallback = recommendations == null;
        if (!fallback) {
            markdown.append("\n## Рекомендации\n\n").append(recommendations).append('\n');
        }
        String path = properties.outputDir() + "/invariants-report.md";
        addDocumentArtifact(session, path, markdown.toString(), !fallback);
        String text = "Проверено " + contents.size() + " файлов, замечаний: " + session.findings.size();
        return new Summary(text, markdown.toString(), fallback);
    }

    private void addDocumentArtifact(Session session, String path, String markdown, boolean fromLlm) {
        String existing = safeRead(path);
        String diff = Day34DiffUtil.unified(path, existing == null ? "" : existing, markdown);
        int bytes = markdown.getBytes(StandardCharsets.UTF_8).length;
        session.artifacts.add(new Day34Artifact(path, existing == null ? "create" : "update",
                markdown, bytes, diff));
        session.evidence.add("Сформирован документ: " + path + " (" + bytes + " байт)"
                + (fromLlm ? "" : " [без модели]"));
    }

    private void persist(Session session, Boolean write) {
        if (!Boolean.TRUE.equals(write)) {
            session.evidence.add("Режим предпросмотра: файлы не записывались (используйте write=true)");
            return;
        }
        for (Day34Artifact artifact : session.artifacts) {
            if (!artifact.changed()) {
                continue;
            }
            Day34WriteResult result = session.ops.write(artifact.path(), artifact.content());
            session.written.add(result.path());
            session.evidence.add("Записано: " + result.path() + " (" + result.action() + ")");
        }
    }

    private List<Day34FileInfo> list(Session session, String prefix, String purpose) {
        try {
            List<Day34FileInfo> files = session.ops.list(prefix, properties.maxFiles());
            record(session, "fs_list", Map.of("prefix", String.valueOf(prefix)), purpose,
                    "файлов: " + files.size(), true);
            session.evidence.add("Список " + prefix + ": " + files.size() + " файлов");
            return files;
        } catch (RuntimeException ex) {
            record(session, "fs_list", Map.of("prefix", String.valueOf(prefix)), purpose, ex.getMessage(), false);
            return List.of();
        }
    }

    private void listInto(Session session, String prefix, String purpose) {
        for (Day34FileInfo file : list(session, prefix, purpose)) {
            session.files.add(file.path());
        }
    }

    private String read(Session session, String path, Integer startLine, Integer maxLines, String purpose) {
        try {
            String content = session.ops.read(path, startLine, maxLines);
            record(session, "fs_read", Map.of("path", path), purpose,
                    "символов: " + (content == null ? 0 : content.length()), true);
            session.files.add(path);
            if (content != null && !content.isBlank()) {
                addContext(session, path, content);
            }
            return content;
        } catch (RuntimeException ex) {
            record(session, "fs_read", Map.of("path", path), purpose, ex.getMessage(), false);
            return null;
        }
    }

    private List<Day34SearchMatch> search(Session session, String pattern, String glob, String purpose) {
        try {
            List<Day34SearchMatch> matches = session.ops.search(pattern, glob, properties.searchMaxResults());
            record(session, "fs_grep", Map.of("pattern", pattern, "glob", String.valueOf(glob)),
                    purpose, "совпадений: " + matches.size(), true);
            return matches;
        } catch (RuntimeException ex) {
            record(session, "fs_grep", Map.of("pattern", pattern), purpose, ex.getMessage(), false);
            return List.of();
        }
    }

    private void record(Session session, String tool, Map<String, Object> arguments, String purpose,
            String summary, boolean ok) {
        int index = session.plan.size() + 1;
        session.plan.add(Day34PlanStep.of(index, tool, arguments, purpose));
        session.steps.add(new Day34StepResult(index, tool, arguments.toString(), summary, ok));
    }

    private void addContext(Session session, String path, String content) {
        int remaining = MAX_CONTEXT_CHARS - session.context.length();
        if (remaining <= 200) {
            return;
        }
        String snippet = content.length() > remaining ? content.substring(0, remaining) : content;
        session.context.append("\n### ").append(path).append("\n").append(snippet).append('\n');
    }

    private String context(Session session) {
        return session.context.isEmpty() ? "(контекст не собран)" : session.context.toString();
    }

    private String contextSummary(Session session) {
        return "Автоматическое обновление выполнено без модели. Затронуто файлов: "
                + session.files.size() + ".";
    }

    private String ask(Session session, String prompt) {
        try {
            Day26Answer answer = llm.chat(List.of(
                    new Day26ChatMessage("system", SYSTEM_PROMPT),
                    new Day26ChatMessage("user", prompt)),
                    Day26ChatOptions.defaults(properties.temperature(), properties.answerMaxTokens()));
            session.llmModel = answer.model() == null ? "" : answer.model();
            session.promptTokens += answer.promptTokens();
            session.outputTokens += answer.outputTokens();
            session.tokensPerSecond = answer.tokensPerSecond();
            String reply = answer.reply();
            if (reply == null || reply.isBlank()) {
                return null;
            }
            return reply.strip();
        } catch (RuntimeException ex) {
            log.warn("День 34: модель недоступна: {}", ex.getMessage());
            session.evidence.add("Модель недоступна: " + ex.getMessage());
            return null;
        }
    }

    private String documentPath(Day34TaskType task, String target) {
        if (target != null && looksLikePath(target)) {
            return target;
        }
        String slug = slug(target == null ? "day34" : target);
        return switch (task) {
            case GENERATE_ADR -> properties.outputDir() + "/adr-" + slug + ".md";
            case GENERATE_CHANGELOG -> properties.outputDir() + "/CHANGELOG-day34.md";
            case GENERATE_README -> properties.outputDir() + "/README-generated.md";
            default -> properties.outputDir() + "/" + slug + ".md";
        };
    }

    private String fallbackDocument(Day34TaskType task, String goal, String target, Session session) {
        StringBuilder text = new StringBuilder();
        text.append("# ").append(task.label()).append("\n\n");
        text.append("> Документ сформирован без языковой модели (модель недоступна).\n\n");
        text.append("**Цель:** ").append(goal).append("\n\n");
        if (target != null && !target.isBlank()) {
            text.append("**Тема:** ").append(target).append("\n\n");
        }
        text.append("## Собранный контекст\n\n");
        text.append("Просмотрено файлов: ").append(session.files.size()).append(".\n\n");
        text.append(context(session));
        return text.toString();
    }

    private String safeRead(String path) {
        try {
            return workspace.exists(path) ? workspace.readText(path) : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String globFor(String target) {
        String lower = target.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) {
            return "*.java";
        }
        if (lower.endsWith(".md")) {
            return "*.md";
        }
        return null;
    }

    private static boolean looksLikePath(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("/") || lower.endsWith(".md") || lower.endsWith(".txt")
                || lower.endsWith(".java") || lower.endsWith(".adoc");
    }

    private static int severityRank(String severity) {
        return switch (severity) {
            case "high" -> 0;
            case "medium" -> 1;
            default -> 2;
        };
    }

    private static int lineCount(String text) {
        return text == null || text.isEmpty() ? 0 : text.split("\n", -1).length;
    }

    private static String truncate(String value) {
        return value.length() <= 4_000 ? value : value.substring(0, 4_000) + "\n…";
    }

    private static String slug(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String toolNames(List<Day34ToolInfo> tools) {
        return tools.stream().map(Day34ToolInfo::name).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private record Summary(String text, String markdown, boolean fallback) {
    }

    private static final class Session {
        Ops ops;
        final List<Day34PlanStep> plan = new ArrayList<>();
        final List<Day34StepResult> steps = new ArrayList<>();
        final List<String> evidence = new ArrayList<>();
        final Set<String> files = new LinkedHashSet<>();
        int matches;
        final List<Day34Finding> findings = new ArrayList<>();
        final List<Day34Artifact> artifacts = new ArrayList<>();
        final List<String> written = new ArrayList<>();
        final StringBuilder context = new StringBuilder();
        String llmModel = "";
        int promptTokens;
        int outputTokens;
        double tokensPerSecond;
    }

    interface Ops {
        String kind();

        List<Day34FileInfo> list(String prefix, Integer limit);

        String read(String path, Integer startLine, Integer maxLines);

        List<Day34SearchMatch> search(String pattern, String glob, Integer maxResults);

        Day34WriteResult write(String path, String content);

        Day34FileInfo stat(String path);
    }

    private record McpOps(Day34McpClient client, Day34Connection connection) implements Ops {

        @Override
        public String kind() {
            return "mcp";
        }

        @Override
        public List<Day34FileInfo> list(String prefix, Integer limit) {
            return client.listFiles(connection, prefix, limit);
        }

        @Override
        public String read(String path, Integer startLine, Integer maxLines) {
            return client.readFile(connection, path, startLine, maxLines);
        }

        @Override
        public List<Day34SearchMatch> search(String pattern, String glob, Integer maxResults) {
            return client.search(connection, pattern, glob, maxResults);
        }

        @Override
        public Day34WriteResult write(String path, String content) {
            return client.writeFile(connection, path, content);
        }

        @Override
        public Day34FileInfo stat(String path) {
            return client.stat(connection, path);
        }
    }

    private record LocalOps(Day34Workspace workspace) implements Ops {

        @Override
        public String kind() {
            return "local";
        }

        @Override
        public List<Day34FileInfo> list(String prefix, Integer limit) {
            return workspace.list(prefix, limit);
        }

        @Override
        public String read(String path, Integer startLine, Integer maxLines) {
            return workspace.content(path, startLine, maxLines).content();
        }

        @Override
        public List<Day34SearchMatch> search(String pattern, String glob, Integer maxResults) {
            return workspace.grep(pattern, glob, maxResults);
        }

        @Override
        public Day34WriteResult write(String path, String content) {
            return workspace.write(path, content);
        }

        @Override
        public Day34FileInfo stat(String path) {
            return workspace.stat(path);
        }
    }
}
