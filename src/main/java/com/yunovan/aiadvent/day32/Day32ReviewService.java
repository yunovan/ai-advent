package com.yunovan.aiadvent.day32;

import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day23.Day23QueryRewriter;
import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day32ReviewService {

    private static final Logger log = LoggerFactory.getLogger(Day32ReviewService.class);

    public static final String SECTION_BUGS = "## Потенциальные баги";
    public static final String SECTION_ARCHITECTURE = "## Архитектурные проблемы";
    public static final String SECTION_RECOMMENDATIONS = "## Рекомендации";

    public static final String SYSTEM =
            "Ты — строгий, но доброжелательный ревьюер кода проекта AI Advent. Тебе дают diff "
                    + "pull request'а, список изменённых файлов и релевантный контекст проекта "
                    + "(документация и код), найденный поиском. Проведи ревью по-русски и верни РОВНО "
                    + "три раздела с заголовками: «" + SECTION_BUGS + "», «" + SECTION_ARCHITECTURE
                    + "», «" + SECTION_RECOMMENDATIONS + "». В каждом разделе — маркерный список "
                    + "пунктов вида «- файл: проблема». Опирайся только на diff и контекст, не "
                    + "выдумывай факты; если замечаний нет — напиши «- нет». Никаких других разделов.";

    private static final Pattern BULLET = Pattern.compile("^([-*•]|\\d+[.)])\\s+(.*)$");
    private static final Pattern FILE = Pattern.compile(
            "([\\w./-]+\\.(?:java|kt|kts|gradle|md|markdown|txt|rst|adoc|yml|yaml|xml|properties|sql|js|ts))");
    private static final Pattern LINE = Pattern.compile(":([0-9]{1,6})\\b");

    private final Day32Properties properties;
    private final Day32ReviewIndex index;
    private final Day32DiffSource git;
    private final Day26LocalLlmClient localLlm;
    private final Day26Properties localProperties;

    public Day32ReviewService(Day32Properties properties, Day32ReviewIndex index, Day32DiffSource git,
                              Day26LocalLlmClient localLlm, Day26Properties localProperties) {
        this.properties = properties;
        this.index = index;
        this.git = git;
        this.localLlm = localLlm;
        this.localProperties = localProperties;
    }

    public Day32HealthResponse health() {
        Day32ReviewIndex.Snapshot snapshot = index.snapshot();
        boolean gitAvailable = git.available();
        String status = gitAvailable && !snapshot.chunks().isEmpty() ? "ok" : "degraded";
        return new Day32HealthResponse(status, git.repositoryDir().toString(),
                gitAvailable ? git.branch() : "", properties.baseRef(), snapshot.documents().size(),
                snapshot.codeCount(), snapshot.chunks().size(), snapshot.corpusChars(), modelStatus());
    }

    public List<Day32ChangedFile> changedFiles(String base, String head) {
        return git.changedFiles(orDefault(base, properties.baseRef()), orDefault(head, properties.headRef()));
    }

    public Day32ReviewResponse review(Day32ReviewRequest request) {
        String base = request == null ? null : request.base();
        String head = request == null ? null : request.head();
        String title = trim(request == null ? null : request.title());
        String description = trim(request == null ? null : request.description());
        String provided = request == null ? null : request.diff();

        String resolvedBase = orDefault(base, properties.baseRef());
        String resolvedHead = orDefault(head, properties.headRef());
        boolean fromRequest = provided != null && !provided.isBlank();
        String rawDiff = fromRequest ? provided : git.diff(base, head);

        List<Day32FileDiff> parsed = Day32DiffParser.parse(rawDiff);
        int cap = properties.maxFiles();
        List<Day32FileDiff> limited = parsed.size() > cap ? parsed.subList(0, cap) : parsed;
        List<Day32ChangedFile> files = limited.stream()
                .map(file -> new Day32ChangedFile(file.path(), file.status(), file.additions(), file.deletions()))
                .toList();
        int additions = parsed.stream().mapToInt(Day32FileDiff::additions).sum();
        int removals = parsed.stream().mapToInt(Day32FileDiff::deletions).sum();

        Day32Heuristics.Result heuristics = Day32Heuristics.analyze(limited);
        if (parsed.isEmpty()) {
            return emptyResponse(resolvedBase, resolvedHead, title, additions, removals,
                    rawDiff.length(), false, heuristics);
        }

        String query = reviewQuery(title, description, limited);
        List<Day21SearchHit> hits = index.search(query, properties.topK());
        List<String> sources = sources(hits);

        boolean truncated = rawDiff.length() > properties.maxDiffChars();
        String diffForPrompt = truncated ? rawDiff.substring(0, properties.maxDiffChars()) : rawDiff;
        String prompt = prompt(title, description, resolvedBase, resolvedHead, limited, additions,
                removals, diffForPrompt, truncated, hits);

        long started = System.nanoTime();
        try {
            Day26Answer answer = localLlm.chat(
                    List.of(new Day26ChatMessage("system", SYSTEM), new Day26ChatMessage("user", prompt)),
                    new Day26ChatOptions(properties.temperature(), properties.answerMaxTokens(), null));
            Sections sections = parseReview(answer.reply());
            List<Day32Finding> bugs = merge(sections.bugs(), heuristics.bugs());
            List<Day32Finding> architecture = merge(sections.architecture(), heuristics.architecture());
            List<Day32Finding> recommendations = merge(sections.recommendations(), heuristics.recommendations());
            String markdown = renderMarkdown(bugs, architecture, recommendations);
            if (!answer.reply().isBlank()) {
                markdown = markdown + "\n\n## Комментарий модели\n" + answer.reply().strip();
            }
            return new Day32ReviewResponse(resolvedBase, resolvedHead, title, files.size(), files,
                    rawDiff.length(), additions, removals, truncated, bugs, architecture, recommendations,
                    summary(false, answer.model(), files.size(), bugs, architecture, recommendations),
                    markdown, false, answer.model(), hits.size(), sources, sources,
                    answer.latencyMs(), answer.promptTokens(), answer.outputTokens(),
                    answer.tokensPerSecond(), "");
        } catch (Day26LlmException ex) {
            long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            log.info("День 32: локальная LLM недоступна, ревью собирается эвристиками: {}",
                    ex.getMessage());
            String markdown = renderMarkdown(heuristics);
            return new Day32ReviewResponse(resolvedBase, resolvedHead, title, files.size(), files,
                    rawDiff.length(), additions, removals, truncated, heuristics.bugs(),
                    heuristics.architecture(), heuristics.recommendations(),
                    summary(true, localProperties.model(), files.size(), heuristics.bugs(),
                            heuristics.architecture(), heuristics.recommendations()),
                    markdown, true, localProperties.model(), hits.size(), sources, sources,
                    elapsed, 0, 0, 0.0, ex.getMessage());
        }
    }

    private Day32ReviewResponse emptyResponse(String base, String head, String title, int additions,
                                              int removals, int diffChars, boolean truncated,
                                              Day32Heuristics.Result heuristics) {
        String summary = additions == 0 && removals == 0
                ? "Изменений для ревью не найдено."
                : "Изменения не распознаны как diff — проверьте диапазон " + base + "..." + head + ".";
        return new Day32ReviewResponse(base, head, title, 0, List.of(), diffChars, additions, removals,
                truncated, heuristics.bugs(), heuristics.architecture(), heuristics.recommendations(),
                summary, renderMarkdown(heuristics), false, localProperties.model(), 0, List.of(),
                List.of(), 0L, 0, 0, 0.0, "");
    }

    public String prompt(String title, String description, String base, String head,
                         List<Day32FileDiff> files, int additions, int removals, String diff,
                         boolean truncated, List<Day21SearchHit> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Pull request: «").append(title.isEmpty() ? "(без названия)" : title).append("»\n");
        prompt.append("Описание: ").append(description.isEmpty() ? "не указано" : description).append("\n");
        prompt.append("Диапазон коммитов: ").append(base).append("...").append(head).append("\n");
        prompt.append("Изменено файлов: ").append(files.size())
                .append(" (добавлено +").append(additions).append(", удалено -").append(removals).append(")\n\n");
        prompt.append("Изменённые файлы:\n");
        for (Day32FileDiff file : files) {
            prompt.append("- ").append(file.path()).append(" (").append(file.status())
                    .append(", +").append(file.additions()).append("/-").append(file.deletions()).append(")\n");
        }
        prompt.append("\nDiff").append(truncated ? " (обрезан по лимиту)" : "").append(":\n```diff\n")
                .append(diff).append("\n```\n\n");
        if (hits.isEmpty()) {
            prompt.append("Релевантный контекст проекта не найден.\n");
        } else {
            prompt.append("Релевантный контекст проекта (RAG: документация + код):\n");
            for (Day21SearchHit hit : hits) {
                prompt.append("\n[Источник: ").append(hit.chunk().source())
                        .append(", чанк: ").append(hit.chunk().chunkId()).append("]\n")
                        .append(hit.chunk().text()).append('\n');
            }
        }
        prompt.append("\nВерни ревью РОВНО тремя разделами: «").append(SECTION_BUGS).append("», «")
                .append(SECTION_ARCHITECTURE).append("», «").append(SECTION_RECOMMENDATIONS)
                .append("». В каждом — маркерный список с указанием файла и строки. "
                        + "Если замечаний нет — «- нет».");
        return prompt.toString();
    }

    private String reviewQuery(String title, String description, List<Day32FileDiff> files) {
        StringBuilder query = new StringBuilder();
        query.append(title).append(' ').append(description);
        for (Day32FileDiff file : files) {
            String path = file.path();
            int slash = path.lastIndexOf('/');
            String name = slash < 0 ? path : path.substring(slash + 1);
            int dot = name.lastIndexOf('.');
            query.append(' ').append(dot < 0 ? name : name.substring(0, dot));
            query.append(' ').append(path);
        }
        return Day23QueryRewriter.normalize(query.toString());
    }

    static Sections parseReview(String text) {
        List<Day32Finding> bugs = new ArrayList<>();
        List<Day32Finding> architecture = new ArrayList<>();
        List<Day32Finding> recommendations = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return new Sections(List.of(), List.of(), List.of());
        }
        String current = "";
        for (String rawLine : text.split("\n")) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                continue;
            }
            String heading = heading(line);
            if (heading.contains("баг") || heading.contains("bug")) {
                current = "bug";
                continue;
            }
            if (heading.contains("архитект") || heading.contains("architect")) {
                current = "arch";
                continue;
            }
            if (heading.contains("рекоменд") || heading.contains("recommend")) {
                current = "rec";
                continue;
            }
            Matcher bullet = BULLET.matcher(line);
            if (!bullet.matches()) {
                continue;
            }
            String body = bullet.group(2).strip();
            if (body.isEmpty() || body.equalsIgnoreCase("нет") || body.equals("-")) {
                continue;
            }
            Day32Finding finding = finding(body, current);
            switch (current) {
                case "bug" -> bugs.add(finding);
                case "arch" -> architecture.add(finding);
                default -> recommendations.add(finding);
            }
        }
        return new Sections(List.copyOf(bugs), List.copyOf(architecture), List.copyOf(recommendations));
    }

    private static String heading(String line) {
        String value = line.toLowerCase(Locale.ROOT);
        while (!value.isEmpty() && (value.charAt(0) == '#' || value.charAt(0) == '*' || value.charAt(0) == ' ')) {
            value = value.substring(1);
        }
        return value.strip();
    }

    private static Day32Finding finding(String body, String category) {
        String lower = body.toLowerCase(Locale.ROOT);
        String severity = "medium";
        if (lower.contains("критич") || lower.contains("высок") || lower.contains("high")) {
            severity = "high";
        } else if (lower.contains("низк") || lower.contains("low") || lower.contains("minor")) {
            severity = "low";
        }
        String file = "";
        Matcher fileMatcher = FILE.matcher(body);
        if (fileMatcher.find()) {
            file = fileMatcher.group(1);
        }
        int line = 0;
        Matcher lineMatcher = LINE.matcher(body);
        if (lineMatcher.find()) {
            line = Integer.parseInt(lineMatcher.group(1));
        }
        String cat = switch (category == null ? "" : category) {
            case "bug" -> "bug";
            case "arch" -> "architecture";
            default -> "recommendation";
        };
        return Day32Finding.of(severity, cat, file, line, body);
    }

    private static List<Day32Finding> merge(List<Day32Finding> first, List<Day32Finding> second) {
        List<Day32Finding> all = new ArrayList<>(first);
        for (Day32Finding finding : second) {
            if (all.stream().noneMatch(existing -> existing.message().equals(finding.message()))) {
                all.add(finding);
            }
        }
        return List.copyOf(all);
    }

    private static String summary(boolean fallback, String model, int files, List<Day32Finding> bugs,
                                  List<Day32Finding> architecture, List<Day32Finding> recommendations) {
        int total = bugs.size() + architecture.size() + recommendations.size();
        String source = fallback ? "эвристиками (LLM недоступна)" : "моделью " + model;
        return "Ревью " + source + ": файлов " + files + ", замечаний " + total
                + " (баги " + bugs.size() + ", архитектура " + architecture.size()
                + ", рекомендации " + recommendations.size() + ").";
    }

    public static String renderMarkdown(Day32Heuristics.Result result) {
        return renderMarkdown(result.bugs(), result.architecture(), result.recommendations());
    }

    public static String renderMarkdown(List<Day32Finding> bugs, List<Day32Finding> architecture,
                                        List<Day32Finding> recommendations) {
        StringBuilder text = new StringBuilder();
        text.append(SECTION_BUGS).append('\n').append(renderList(bugs)).append("\n\n");
        text.append(SECTION_ARCHITECTURE).append('\n').append(renderList(architecture)).append("\n\n");
        text.append(SECTION_RECOMMENDATIONS).append('\n').append(renderList(recommendations));
        return text.toString().trim();
    }

    private static String renderList(List<Day32Finding> findings) {
        if (findings.isEmpty()) {
            return "- нет";
        }
        StringBuilder text = new StringBuilder();
        for (Day32Finding finding : findings) {
            text.append("- ");
            if (!finding.file().isBlank()) {
                text.append(finding.file());
                if (finding.line() > 0) {
                    text.append(':').append(finding.line());
                }
                text.append(" — ");
            }
            text.append(finding.message()).append('\n');
        }
        return text.toString().trim();
    }

    private static List<String> sources(List<Day21SearchHit> hits) {
        return hits.stream().map(hit -> hit.chunk().fileName()).distinct().toList();
    }

    private Day32ModelStatus modelStatus() {
        try {
            String version = localLlm.version();
            boolean installed = localLlm.models().stream()
                    .anyMatch(model -> model.name() != null
                            && model.name().equalsIgnoreCase(localProperties.model()));
            return new Day32ModelStatus(localProperties.endpoint(), localProperties.model(),
                    true, version, installed, "");
        } catch (Day26LlmException ex) {
            return new Day32ModelStatus(localProperties.endpoint(), localProperties.model(),
                    false, "", false, ex.getMessage());
        } catch (RuntimeException ex) {
            return new Day32ModelStatus(localProperties.endpoint(), localProperties.model(),
                    false, "", false, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String trim(String value) {
        return value == null ? "" : value.strip();
    }

    public record Sections(List<Day32Finding> bugs, List<Day32Finding> architecture,
                           List<Day32Finding> recommendations) {
    }
}
