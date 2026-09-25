package com.yunovan.aiadvent.day19;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Day19SaveService {

    private static final Logger log = LoggerFactory.getLogger(Day19SaveService.class);

    private final Day19Properties properties;

    public Day19SaveService(Day19Properties properties) {
        this.properties = properties;
    }

    public Day19SavedFile save(String summary, String data, String format, String fileName) {
        String fmt = normalizeFormat(format);
        String body = bodyFor(summary, data, fmt);
        String file = safeFileName(fileName, extension(fmt));
        Path dir = Path.of(properties.storeDir()).toAbsolutePath().normalize();
        Path target = dir.resolve(file);
        try {
            Files.createDirectories(dir);
            Files.writeString(target, body, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось сохранить файл " + target + ": " + ex.getMessage(), ex);
        }
        log.info("День 19: сводная таблица сохранена в {}", target);
        return new Day19SavedFile(file, target.toString(), fmt, Files.exists(target) ? target.toFile().length() : 0,
                preview(body));
    }

    public List<Day19SavedFile> list() {
        Path dir = Path.of(properties.storeDir()).toAbsolutePath().normalize();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Day19SavedFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> {
                        long bytes = path.toFile().length();
                        files.add(new Day19SavedFile(path.getFileName().toString(), path.toString(),
                                extensionOf(path), bytes, null));
                    });
        } catch (IOException ex) {
            log.info("День 19: не удалось прочитать каталог файлов: {}", ex.getMessage());
        }
        return files;
    }

    private static String bodyFor(String summary, String data, String format) {
        switch (format) {
            case "markdown", "txt":
                if (summary == null || summary.isBlank()) {
                    throw new IllegalArgumentException("Нечего сохранять: передайте результат summarize");
                }
                return summary;
            case "csv":
            case "json":
                List<Day19Product> products = new Day19CatalogService().parseJson(data);
                return "csv".equals(format) ? Day19TableBuilder.csv(products) : prettyJson(products);
            default:
                throw new IllegalArgumentException("Неизвестный формат: " + format);
        }
    }

    private static String prettyJson(List<Day19Product> products) {
        ObjectNode root = new ObjectMapper().createObjectNode();
        root.put("products", new ObjectMapper().valueToTree(products));
        try {
            return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception ex) {
            return root.toString();
        }
    }

    private static String normalizeFormat(String format) {
        String fmt = format == null ? "markdown" : format.trim().toLowerCase(Locale.ROOT);
        return switch (fmt) {
            case "md" -> "markdown";
            case "text" -> "txt";
            default -> fmt;
        };
    }

    private static String extension(String format) {
        return switch (format) {
            case "markdown" -> "md";
            case "txt" -> "txt";
            case "csv" -> "csv";
            case "json" -> "json";
            default -> "txt";
        };
    }

    private static String safeFileName(String fileName, String extension) {
        String base = fileName == null ? "" : fileName.trim();
        base = base.replaceAll("[^\\p{L}\\p{N}\\-_., ]", "-").replaceAll("\\s+", " ");
        if (base.isBlank() || ".".equals(base)) {
            base = "comparison";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        base = base.replaceFirst("^[\\-. ]+", "").trim();
        if (base.isBlank()) {
            base = "comparison";
        }
        return base.endsWith("." + extension) ? base : base + "." + extension;
    }

    private static String preview(String body) {
        String singleLine = body.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        return singleLine.length() <= 200 ? singleLine : singleLine.substring(0, 200) + "…";
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "txt" : name.substring(dot + 1);
    }
}