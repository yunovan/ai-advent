package com.yunovan.aiadvent.day34;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day34Workspace {

    private static final Logger log = LoggerFactory.getLogger(Day34Workspace.class);

    private static final Set<String> SKIPPED_DIRS = Set.of(
            ".git", ".gradle", ".idea", "build", "node_modules", "out", "target", ".venv", "allure-results");

    private final Day34Properties properties;

    public Day34Workspace(Day34Properties properties) {
        this.properties = properties;
    }

    public Path root() {
        return Path.of(properties.workspaceDir()).toAbsolutePath().normalize();
    }

    public boolean available() {
        return Files.isDirectory(root());
    }

    public String relative(Path file) {
        return root().relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    public List<Day34FileInfo> list(String prefix, Integer limit) {
        String normalizedPrefix = prefix == null ? "" : prefix.trim().replace('\\', '/');
        int cap = limit == null || limit <= 0 ? properties.maxFiles() : Math.min(limit, properties.maxFiles());
        List<Day34FileInfo> result = new ArrayList<>();
        for (Path file : collect()) {
            String relative = relative(file);
            if (!normalizedPrefix.isEmpty() && !relative.startsWith(normalizedPrefix)) {
                continue;
            }
            if (result.size() >= cap) {
                break;
            }
            result.add(info(file, relative));
        }
        return result;
    }

    public Day34FileContent content(String path, Integer startLine, Integer maxLines) {
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) {
            throw new Day34Exception("файл не найден: " + path);
        }
        String text = readText(file);
        List<String> all = splitLines(text);
        int from = startLine == null || startLine < 1 ? 1 : startLine;
        int limit = maxLines == null || maxLines <= 0 ? properties.maxReadLines() : maxLines;
        limit = Math.min(limit, properties.maxReadLines());
        if (from > all.size()) {
            return new Day34FileContent(relative(file), from, from - 1, all.size(), "", false);
        }
        int to = Math.min(all.size(), from - 1 + limit);
        List<String> slice = all.subList(from - 1, to);
        boolean truncated = to < all.size();
        return new Day34FileContent(relative(file), from, to, all.size(), String.join("\n", slice), truncated);
    }

    public String readText(String path) {
        return readText(resolve(path));
    }

    public List<Day34SearchMatch> grep(String pattern, String glob, Integer maxResults) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("Шаблон поиска не может быть пустым");
        }
        Pattern regex = compile(pattern);
        Pattern globRegex = globRegex(glob);
        int cap = maxResults == null || maxResults <= 0
                ? properties.searchMaxResults() : Math.min(maxResults, properties.searchMaxResults());
        List<Day34SearchMatch> matches = new ArrayList<>();
        for (Path file : collect()) {
            String relative = relative(file);
            if (globRegex != null && !globRegex.matcher(relative).matches()) {
                continue;
            }
            List<String> lines;
            try {
                lines = splitLines(readText(file));
            } catch (RuntimeException ex) {
                continue;
            }
            for (int i = 0; i < lines.size(); i++) {
                if (regex.matcher(lines.get(i)).find()) {
                    matches.add(new Day34SearchMatch(relative, i + 1, lines.get(i).strip()));
                    if (matches.size() >= cap) {
                        return matches;
                    }
                }
            }
        }
        return matches;
    }

    public boolean exists(String path) {
        return Files.isRegularFile(resolve(path));
    }

    public Day34FileInfo stat(String path) {
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) {
            throw new Day34Exception("файл не найден: " + path);
        }
        return info(file, relative(file));
    }

    public Day34WriteResult write(String path, String content) {
        Path file = resolve(path);
        String value = content == null ? "" : content;
        boolean existed = Files.isRegularFile(file);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, value, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new Day34Exception("не удалось записать файл " + path + ": " + ex.getMessage(), ex);
        }
        String action = existed ? "update" : "create";
        log.info("День 34: файл {} ({})", path, action);
        return new Day34WriteResult(relative(file), action,
                value.getBytes(StandardCharsets.UTF_8).length);
    }

    public String patch(String path, String search, String replace) {
        if (search == null || search.isEmpty()) {
            throw new IllegalArgumentException("Фрагмент для замены не может быть пустым");
        }
        Path file = resolve(path);
        if (!Files.isRegularFile(file)) {
            throw new Day34Exception("файл не найден: " + path);
        }
        String text = readText(file);
        if (!text.contains(search)) {
            throw new Day34Exception("фрагмент не найден в " + path);
        }
        String updated = text.replaceFirst(Pattern.quote(search),
                replace == null ? "" : java.util.regex.Matcher.quoteReplacement(replace));
        try {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new Day34Exception("не удалось обновить файл " + path + ": " + ex.getMessage(), ex);
        }
        return updated;
    }

    private Path resolve(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Путь к файлу не может быть пустым");
        }
        Path candidate = root().resolve(path.trim()).normalize();
        if (!candidate.startsWith(root())) {
            throw new Day34Exception("путь вне рабочего каталога: " + path);
        }
        if (candidate.getFileName() != null
                && !Day34Properties.isAllowed(candidate.getFileName().toString())) {
            throw new Day34Exception("расширение файла не поддерживается: " + candidate.getFileName());
        }
        return candidate;
    }

    private List<Path> collect() {
        Path root = root();
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(this::supported)
                    .filter(file -> !skipped(root, file))
                    .filter(file -> size(file) <= properties.maxFileBytes())
                    .sorted()
                    .limit(properties.maxFiles() * 5L)
                    .toList();
        } catch (IOException ex) {
            log.warn("День 34: не удалось прочитать рабочий каталог {}: {}", root, ex.getMessage());
            return List.of();
        }
    }

    private boolean skipped(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path segment : relative) {
            if (SKIPPED_DIRS.contains(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean supported(Path file) {
        return Day34Properties.isAllowed(file.getFileName().toString());
    }

    private Day34FileInfo info(Path file, String relative) {
        long size;
        String modified;
        try {
            size = Files.size(file);
            modified = Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).toString();
        } catch (IOException ex) {
            size = -1;
            modified = "";
        }
        int lines = 0;
        try {
            lines = splitLines(readText(file)).size();
        } catch (RuntimeException ignored) {
            lines = 0;
        }
        return new Day34FileInfo(relative, size, lines, modified);
    }

    private String readText(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new Day34Exception("файл не найден: " + file);
        }
        if (!Day34Properties.isAllowed(file.getFileName().toString())) {
            throw new Day34Exception("расширение файла не поддерживается: " + file.getFileName());
        }
        try {
            if (Files.size(file) > properties.maxFileBytes()) {
                throw new Day34Exception("файл слишком большой: " + file.getFileName());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new Day34Exception("не удалось прочитать файл " + file + ": " + ex.getMessage(), ex);
        }
    }

    private static List<String> splitLines(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        for (String line : text.split("\n", -1)) {
            result.add(line);
        }
        if (!result.isEmpty() && result.getLast().isEmpty()) {
            result.removeLast();
        }
        return result;
    }

    private static Pattern compile(String pattern) {
        try {
            return Pattern.compile(pattern);
        } catch (PatternSyntaxException ex) {
            return Pattern.compile(Pattern.quote(pattern));
        }
    }

    private static Pattern globRegex(String glob) {
        if (glob == null || glob.isBlank()) {
            return null;
        }
        String normalized = glob.trim().replace('\\', '/');
        if (!normalized.contains("/") && !normalized.contains("*")) {
            normalized = "*." + normalized;
        }
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '+', '|', '^', '$', '@', '%' -> regex.append('\\').append(ch);
                default -> regex.append(ch);
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return Long.MAX_VALUE;
        }
    }
}
