package com.yunovan.aiadvent.day32;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21Chunker;
import com.yunovan.aiadvent.day21.Day21Document;
import com.yunovan.aiadvent.day21.Day21Embedding;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21FixedChunker;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day23.Day23QueryRewriter;
import com.yunovan.aiadvent.day23.Day23ReRanker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day32ReviewIndex {

    private static final Logger log = LoggerFactory.getLogger(Day32ReviewIndex.class);

    private static final Pattern MARKDOWN_TITLE = Pattern.compile("^\\s*#\\s+(.+)$", Pattern.MULTILINE);
    private static final Set<String> DOC_EXTENSIONS = Set.of("md", "markdown", "txt", "rst", "adoc");
    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "kts", "gradle", "xml", "yml", "yaml", "properties", "sql", "js", "ts");
    private static final int MAX_CHUNKS_PER_FILE = 2;

    private final Day32Properties properties;
    private final Day21EmbeddingService embeddings;
    private final Day21Chunker chunker;
    private final Object lock = new Object();

    private volatile Snapshot snapshot;

    public Day32ReviewIndex(Day32Properties properties, Day21EmbeddingService embeddings) {
        this.properties = properties;
        this.embeddings = embeddings;
        this.chunker = new Day21FixedChunker(new Day21Properties(null, null, null, null, null, null,
                properties.chunkSize(), properties.chunkOverlap(), null));
    }

    public Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            synchronized (lock) {
                current = snapshot;
                if (current == null) {
                    current = build();
                    snapshot = current;
                }
            }
        }
        return current;
    }

    public void reindex() {
        synchronized (lock) {
            snapshot = null;
        }
    }

    public List<Day21SearchHit> search(String query, int k) {
        Snapshot current = snapshot();
        int limit = k <= 0 ? properties.topK() : k;
        if (query == null || query.isBlank() || current.entries().isEmpty()) {
            return List.of();
        }
        String matched = Day23QueryRewriter.rewrite(query).rewritten();
        Day21Embedding queryVector = embeddings.embed(matched);
        Map<String, Double> dense = new LinkedHashMap<>();
        List<Day21SearchHit> denseHits = new ArrayList<>();
        for (Entry entry : current.entries()) {
            double score = Day21EmbeddingService.cosine(queryVector, entry.vector());
            if (score > 0) {
                dense.put(entry.chunk().chunkId(), score);
                denseHits.add(new Day21SearchHit(entry.chunk(), score, snippet(entry.chunk().text())));
            }
        }
        List<Day21SearchHit> ranked = Day23ReRanker.rank(matched, current.chunks(), dense);
        if (ranked.isEmpty()) {
            denseHits.sort(Comparator.comparingDouble(Day21SearchHit::score).reversed());
            ranked = denseHits;
        }
        return diversify(ranked, limit);
    }

    private static List<Day21SearchHit> diversify(List<Day21SearchHit> ranked, int limit) {
        List<Day21SearchHit> chosen = new ArrayList<>();
        Map<String, Integer> perFile = new HashMap<>();
        for (Day21SearchHit hit : ranked) {
            if (chosen.size() == limit) {
                break;
            }
            String file = hit.chunk().fileName();
            int used = perFile.getOrDefault(file, 0);
            if (used >= MAX_CHUNKS_PER_FILE) {
                continue;
            }
            perFile.put(file, used + 1);
            chosen.add(hit);
        }
        if (chosen.size() < limit) {
            for (Day21SearchHit hit : ranked) {
                if (chosen.size() == limit) {
                    break;
                }
                if (!chosen.contains(hit)) {
                    chosen.add(hit);
                }
            }
        }
        return chosen;
    }

    private Snapshot build() {
        List<Path> docFiles = collect(repositoryDir().resolve(properties.docsDir()), DOC_EXTENSIONS);
        Path readme = repositoryDir().resolve(properties.readmePath()).normalize();
        List<Path> codeFiles = collect(repositoryDir().resolve(properties.codeDir()), CODE_EXTENSIONS);

        List<Day21Document> documents = new ArrayList<>();
        int docCount = 0;
        if (Files.isRegularFile(readme)) {
            Day21Document document = document(repositoryDir(), readme);
            if (document != null) {
                documents.add(document);
                docCount++;
            }
        }
        for (Path file : docFiles) {
            Day21Document document = document(repositoryDir(), file);
            if (document != null) {
                documents.add(document);
                docCount++;
            }
        }
        int codeCount = 0;
        for (Path file : codeFiles) {
            if (file.equals(readme)) {
                continue;
            }
            Day21Document document = document(repositoryDir(), file);
            if (document != null) {
                documents.add(document);
                codeCount++;
            }
        }

        List<Day21Chunk> chunks = new ArrayList<>();
        for (Day21Document document : documents) {
            chunks.addAll(chunker.chunk(document));
        }
        List<Entry> entries = new ArrayList<>(chunks.size());
        for (Day21Chunk chunk : chunks) {
            entries.add(new Entry(chunk, embeddings.embed(chunk.text())));
        }
        int corpusChars = documents.stream().mapToInt(document -> document.text().length()).sum();
        log.info("День 32: индекс для ревью собран — {} документов ({} кода), {} чанков, {} символов",
                documents.size(), codeCount, chunks.size(), corpusChars);
        return new Snapshot(List.copyOf(documents), List.copyOf(chunks), List.copyOf(entries),
                corpusChars, docCount, codeCount);
    }

    private List<Path> collect(Path dir, Set<String> extensions) {
        Path normalized = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(normalized)) {
            return stream.filter(Files::isRegularFile)
                    .filter(file -> supported(file, extensions))
                    .filter(file -> size(file) <= Math.max(1_000, properties.maxDiffChars() * 3L))
                    .sorted()
                    .limit(properties.maxFiles() * 5L)
                    .toList();
        } catch (IOException ex) {
            log.warn("День 32: не удалось прочитать каталог {}: {}", normalized, ex.getMessage());
            return List.of();
        }
    }

    private boolean supported(Path file, Set<String> extensions) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return extensions.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return Long.MAX_VALUE;
        }
    }

    private Day21Document document(Path repo, Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                return null;
            }
            String source = repo.relativize(file).toString().replace('\\', '/');
            String fileName = file.getFileName().toString();
            return new Day21Document(source, fileName, titleOf(text, fileName), typeOf(fileName), text);
        } catch (IOException ex) {
            log.warn("День 32: не удалось прочитать файл {}: {}", file, ex.getMessage());
            return null;
        }
    }

    private static String titleOf(String text, String fallback) {
        Matcher matcher = MARKDOWN_TITLE.matcher(text);
        if (matcher.find()) {
            String title = matcher.group(1).trim();
            if (!title.isEmpty()) {
                return title;
            }
        }
        return fallback;
    }

    private static String typeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "text" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String snippet(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240).trim() + "…";
    }

    private Path repositoryDir() {
        return Path.of(properties.repositoryDir()).toAbsolutePath().normalize();
    }

    public record Entry(Day21Chunk chunk, Day21Embedding vector) {
    }

    public record Snapshot(List<Day21Document> documents, List<Day21Chunk> chunks,
                           List<Entry> entries, int corpusChars, int docCount, int codeCount) {
    }
}
