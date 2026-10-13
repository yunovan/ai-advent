package com.yunovan.aiadvent.day31;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day31GitTool {

    private static final Logger log = LoggerFactory.getLogger(Day31GitTool.class);

    private final Day31Properties properties;

    public Day31GitTool(Day31Properties properties) {
        this.properties = properties;
    }

    public boolean available() {
        Path repo = repositoryDir();
        return Files.isDirectory(repo) && Files.isDirectory(repo.resolve(".git"));
    }

    public Path repositoryDir() {
        return Path.of(properties.repositoryDir()).toAbsolutePath().normalize();
    }

    public Day31GitInfo info() {
        if (!available()) {
            return Day31GitInfo.unavailable("git-репозиторий не найден: " + repositoryDir());
        }
        try {
            String branch = branch();
            String commit = firstLine(run(List.of("git", "rev-parse", "--short", "HEAD")));
            String status = run(List.of("git", "status", "--porcelain"));
            return new Day31GitInfo(true, branch, commit, !status.isBlank(), "");
        } catch (Day31GitException ex) {
            return Day31GitInfo.unavailable(ex.getMessage());
        }
    }

    public String branch() {
        String value = firstLine(run(List.of("git", "rev-parse", "--abbrev-ref", "HEAD")));
        if ("HEAD".equals(value)) {
            String sha = firstLine(run(List.of("git", "rev-parse", "--short", "HEAD")));
            return "detached HEAD @" + sha;
        }
        return value;
    }

    public String status() {
        String branch = branch();
        String porcelain = run(List.of("git", "status", "--short"));
        long changed = porcelain.isBlank()
                ? 0 : porcelain.lines().filter(line -> !line.startsWith("??")).count();
        long untracked = porcelain.isBlank()
                ? 0 : porcelain.lines().filter(line -> line.startsWith("??")).count();
        StringBuilder text = new StringBuilder();
        text.append("Ветка: ").append(branch).append('\n');
        text.append("Изменённых файлов: ").append(changed)
                .append(", новых (untracked): ").append(untracked).append('\n');
        if (porcelain.isBlank()) {
            text.append("Рабочее дерево чистое — незакоммиченных изменений нет.");
        } else {
            text.append("git status --short:\n").append(porcelain);
        }
        return text.toString().trim();
    }

    public String files(String prefix, Integer limit) {
        List<String> command = new ArrayList<>(List.of("git", "ls-files"));
        if (prefix != null && !prefix.isBlank()) {
            command.add(prefix.trim());
        }
        String output = run(command);
        List<String> lines = output.isBlank() ? List.of() : output.lines().toList();
        int cap = limit == null || limit <= 0
                ? properties.maxFiles() : Math.min(limit, properties.maxFiles());
        List<String> shown = lines.stream().limit(cap).toList();
        StringBuilder text = new StringBuilder();
        text.append("Отслеживаемых файлов: ").append(lines.size());
        if (prefix != null && !prefix.isBlank()) {
            text.append(" (фильтр: ").append(prefix.trim()).append(')');
        }
        text.append('\n');
        if (shown.isEmpty()) {
            text.append("Ничего не найдено.");
        } else {
            shown.forEach(line -> text.append(line).append('\n'));
            if (lines.size() > shown.size()) {
                text.append("… ещё ").append(lines.size() - shown.size()).append(" файлов скрыто.");
            }
        }
        return text.toString().trim();
    }

    public String diff(boolean staged, Integer maxChars) {
        List<String> command = new ArrayList<>(List.of("git", "diff", "--no-color"));
        if (staged) {
            command.add("--cached");
        }
        String output = run(command);
        String scope = staged ? "проиндексированные" : "рабочего дерева";
        if (output.isBlank()) {
            return "Изменений (" + scope + ") нет.";
        }
        int cap = maxChars == null || maxChars <= 0
                ? properties.maxDiffChars() : maxChars;
        if (output.length() > cap) {
            return output.substring(0, cap).trim()
                    + "\n… diff обрезан до " + cap + " символов (всего " + output.length() + ").";
        }
        return output;
    }

    public String log(Integer limit) {
        int count = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);
        String output = run(List.of("git", "log", "--oneline", "--no-color", "-n", String.valueOf(count)));
        if (output.isBlank()) {
            return "История коммитов пуста.";
        }
        return "Последние коммиты (не более " + count + "):\n" + output;
    }

    private String run(List<String> command) {
        if (!available()) {
            throw new Day31GitException("git недоступен: репозиторий не найден в " + repositoryDir());
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(repositoryDir().toFile());
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new Day31GitException("не удалось запустить git: " + ex.getMessage(), ex);
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var input = process.getInputStream()) {
                input.transferTo(buffer);
            } catch (IOException ignored) {
                log.debug("Поток git закрылся преждевременно");
            }
        });
        try {
            boolean finished = process.waitFor(properties.gitTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new Day31GitException("git не успел ответить за " + properties.gitTimeoutMs()
                        + " мс: " + String.join(" ", command));
            }
            reader.join(1_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new Day31GitException("выполнение git прервано", ex);
        }
        String output = buffer.toString(StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new Day31GitException(output.isEmpty()
                    ? "git завершился с кодом " + process.exitValue()
                    : output);
        }
        return output;
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        int newline = text.indexOf('\n');
        return (newline < 0 ? text : text.substring(0, newline)).trim();
    }
}
