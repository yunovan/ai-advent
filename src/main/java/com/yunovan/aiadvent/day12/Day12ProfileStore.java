package com.yunovan.aiadvent.day12;

import com.yunovan.aiadvent.agent.dialog.DialogStoreException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

public class Day12ProfileStore {

    private static final Pattern UNSAFE = Pattern.compile("[^\\p{L}\\p{N}]");

    private static final Map<Character, String> TRANSLIT = Map.ofEntries(
            Map.entry('а', "a"), Map.entry('б', "b"), Map.entry('в', "v"), Map.entry('г', "g"),
            Map.entry('д', "d"), Map.entry('е', "e"), Map.entry('ё', "yo"), Map.entry('ж', "zh"),
            Map.entry('з', "z"), Map.entry('и', "i"), Map.entry('й', "y"), Map.entry('к', "k"),
            Map.entry('л', "l"), Map.entry('м', "m"), Map.entry('н', "n"), Map.entry('о', "o"),
            Map.entry('п', "p"), Map.entry('р', "r"), Map.entry('с', "s"), Map.entry('т', "t"),
            Map.entry('у', "u"), Map.entry('ф', "f"), Map.entry('х', "kh"), Map.entry('ц', "ts"),
            Map.entry('ч', "ch"), Map.entry('ш', "sh"), Map.entry('щ', "shch"), Map.entry('ъ', ""),
            Map.entry('ы', "y"), Map.entry('ь', ""), Map.entry('э', "e"), Map.entry('ю', "yu"),
            Map.entry('я', "ya"));

    private final JsonMapper objectMapper;
    private final Path root;

    public Day12ProfileStore(Path root) {
        this.objectMapper = JsonMapper.builder().build();
        this.root = root;
    }

    public synchronized Day12Profile create(
            String name, String style, String format, List<String> restrictions, String notes) {
        Day12Profile profile = new Day12Profile(uniqueId(name), name, style, format, restrictions, notes, null);
        save(profile);
        return profile;
    }

    public synchronized void save(Day12Profile profile) {
        try {
            Files.createDirectories(root);
            objectMapper.writeValue(fileFor(profile.id()).toFile(), profile);
        } catch (Exception ex) {
            throw new DialogStoreException(
                    "Failed to save profile '" + profile.id() + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized Day12Profile find(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        Day12Profile profile = readQuiet(fileFor(id));
        return profile != null && profile.usable() ? profile : null;
    }

    public synchronized Day12Profile findByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim().toLowerCase();
        return all().stream()
                .filter(profile -> profile.name().toLowerCase().equals(normalized))
                .findFirst()
                .orElse(null);
    }

    public synchronized boolean delete(String id) {
        try {
            return Files.deleteIfExists(fileFor(id));
        } catch (IOException ex) {
            throw new DialogStoreException("Failed to delete profile '" + id + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized List<Day12Profile> all() {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(root)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .map(this::readQuiet)
                    .filter(Objects::nonNull)
                    .filter(Day12Profile::usable)
                    .sorted(Comparator.comparing(Day12Profile::name))
                    .toList();
        } catch (IOException ex) {
            return List.of();
        }
    }

    public synchronized void seedIfEmpty() {
        if (!all().isEmpty()) {
            return;
        }
        Day12Profile asya = new Day12Profile(
                "asya", "Ася",
                "кратко и по делу",
                "списки, шаги 1-2-3",
                List.of("без жаргона", "без эмодзи", "только русский"),
                "системный аналитик, 5 лет опыта", null);
        Day12Profile manager = new Day12Profile(
                "manager", "Менеджер",
                "развёрнуто и формально",
                "отчёт с заголовками и таблицей сроков",
                List.of("без сокращений", "минимум канцелярита"),
                "руководитель проекта, ждёт исполнительские сроки", null);
        Day12Profile dev = new Day12Profile(
                "dev", "Разработчик",
                "точно и с деталями",
                "код, термины, минимум воды",
                List.of("без повторов", "без маркетинговых формулировок"),
                "Java/Spring backend-инженер", null);
        save(asya);
        save(manager);
        save(dev);
    }

    private Day12Profile readQuiet(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Day12Profile.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String uniqueId(String name) {
        String base = slug(name);
        if (find(base) == null) {
            return base;
        }
        String suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        return base + "-" + suffix;
    }

    private static String slug(String name) {
        if (name == null || name.isBlank()) {
            return "profile";
        }
        StringBuilder latin = new StringBuilder();
        for (char ch : name.trim().toLowerCase().toCharArray()) {
            String mapped = TRANSLIT.get(ch);
            latin.append(mapped != null ? mapped : ch);
        }
        String safe = UNSAFE.matcher(latin).replaceAll("_");
        safe = safe.replaceAll("_+$", "");
        if (safe.isEmpty()) {
            safe = "profile";
        }
        if (safe.length() > 48) {
            safe = safe.substring(0, 48);
        }
        return safe;
    }

    private Path fileFor(String id) {
        return root.resolve(slug(id) + ".json");
    }
}