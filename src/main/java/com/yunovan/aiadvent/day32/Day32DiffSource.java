package com.yunovan.aiadvent.day32;

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
public class Day32DiffSource {

    private static final Logger log = LoggerFactory.getLogger(Day32DiffSource.class);

    private final Day32Properties properties;

    public Day32DiffSource(Day32Properties properties) {
        this.properties = properties;
    }

    public boolean available() {
        Path repo = repositoryDir();
        return Files.isDirectory(repo) && Files.isDirectory(repo.resolve(".git"));
    }

    public Path repositoryDir() {
        return Path.of(properties.repositoryDir()).toAbsolutePath().normalize();
    }

    public String branch() {
        String value = firstLine(run(List.of("git", "rev-parse", "--abbrev-ref", "HEAD")));
        return "HEAD".equals(value) ? "detached HEAD" : value;
    }

    public String diff(String base, String head) {
        List<String> command = new ArrayList<>(List.of("git", "diff", "--no-color"));
        if (base == null || base.isBlank()) {
            String target = head == null || head.isBlank() ? "HEAD" : head.trim();
            command.add(target);
        } else {
            String from = base.trim();
            String to = head == null || head.isBlank() ? properties.headRef() : head.trim();
            command.add(from + "..." + to);
        }
        return run(command);
    }

    public List<Day32ChangedFile> changedFiles(String base, String head) {
        return Day32DiffParser.parse(diff(base, head)).stream()
                .map(file -> new Day32ChangedFile(file.path(), file.status(),
                        file.additions(), file.deletions()))
                .toList();
    }

    private String run(List<String> command) {
        if (!available()) {
            throw new Day32GitException("git недоступен: репозиторий не найден в " + repositoryDir());
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(repositoryDir().toFile());
        builder.redirectErrorStream(true);
        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            throw new Day32GitException("не удалось запустить git: " + ex.getMessage(), ex);
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
                throw new Day32GitException("git не успел ответить за " + properties.gitTimeoutMs()
                        + " мс: " + String.join(" ", command));
            }
            reader.join(1_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new Day32GitException("выполнение git прервано", ex);
        }
        String output = buffer.toString(StandardCharsets.UTF_8).trim();
        if (process.exitValue() != 0) {
            throw new Day32GitException(output.isEmpty()
                    ? "git завершился с кодом " + process.exitValue() : output);
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
