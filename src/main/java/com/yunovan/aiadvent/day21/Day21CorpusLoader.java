package com.yunovan.aiadvent.day21;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class Day21CorpusLoader {

    private static final Logger log = LoggerFactory.getLogger(Day21CorpusLoader.class);

    private static final String CORPUS_CLASSPATH = "day21-corpus/";

    private static final List<String> BUNDLED = List.of(
            "project/README.md",
            "articles/chunking.md",
            "articles/embeddings.md",
            "articles/mcp.md",
            "articles/memory.md",
            "articles/rag-pipeline.md",
            "code/TokenizerSample.java",
            "code/EmbeddingSample.java",
            "code/QueuedLogger.java");

    private static final List<String> PDF_GUIDE_LINES = List.of(
            "AI Advent - Day 21 indexation guide",
            "This tiny PDF is generated at runtime to prove that",
            "documents in PDF format can be converted to plain text",
            "and included into the local index without external tools.",
            "The extractor parses uncompressed text streams and can",
            "also inflate FlateDecode streams from real PDF files.",
            "Chunking, embeddings and metadata work identically",
            "for markdown articles, source code and PDF text.");

    private static final Pattern FIRST_HEADING = Pattern.compile("(?m)^#\\s+(.+)$");

    private final Day21Properties properties;

    public Day21CorpusLoader(Day21Properties properties) {
        this.properties = properties;
    }

    public synchronized List<Day21Document> loadDocuments() {
        materialize();
        Path root = Path.of(properties.corpusDir());
        List<Day21Document> documents = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return documents;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .sorted()
                    .forEach(path -> {
                        Day21Document parsed = parse(path);
                        if (parsed != null) {
                            documents.add(parsed);
                        }
                    });
        } catch (IOException ex) {
            log.warn("День 21: не удалось обойти корпус {}: {}", root, ex.getMessage());
        }
        return documents;
    }

    public synchronized void materialize() {
        Path root = Path.of(properties.corpusDir());
        try {
            if (Files.isDirectory(root)) {
                ensurePdf(root);
                return;
            }
            Files.createDirectories(root);
            for (String relative : BUNDLED) {
                copyIfMissing(relative, root.resolve(relative));
            }
            ensurePdf(root);
            log.info("День 21: корпус сохранён в {} ({} документов)",
                    root, BUNDLED.size() + 1);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось развернуть корпус в " + root, ex);
        }
    }

    private void copyIfMissing(String classpath, Path target) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        ClassPathResource resource = new ClassPathResource(CORPUS_CLASSPATH + classpath);
        if (!resource.exists()) {
            log.warn("День 21: встроенный документ отсутствует: {}", classpath);
            return;
        }
        Files.createDirectories(target.getParent());
        try (InputStream input = resource.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void ensurePdf(Path root) throws IOException {
        Path pdf = root.resolve("pdf/guide.pdf");
        if (Files.exists(pdf)) {
            return;
        }
        Files.createDirectories(pdf.getParent());
        Files.write(pdf, Day21PdfWriter.generate(PDF_GUIDE_LINES));
    }

    private Day21Document parse(Path path) {
        String fileName = path.getFileName().toString();
        if (fileName.startsWith(".")) {
            return null;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        String type;
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            type = "markdown";
        } else if (lower.endsWith(".pdf")) {
            type = "pdf";
        } else if (lower.endsWith(".txt") || lower.endsWith(".text")) {
            type = "plain";
        } else if (lower.endsWith(".java") || lower.endsWith(".kt") || lower.endsWith(".py")
                || lower.endsWith(".js") || lower.endsWith(".json") || lower.endsWith(".yml")
                || lower.endsWith(".yaml") || lower.endsWith(".sql") || lower.endsWith(".html")) {
            type = "code";
        } else {
            return null;
        }
        String source = relativeSource(path);
        String text;
        try {
            if ("pdf".equals(type)) {
                text = Day21PdfText.extract(Files.readAllBytes(path));
            } else {
                text = Files.readString(path, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            log.warn("День 21: не удалось прочитать {}: {}", path, ex.getMessage());
            return null;
        }
        String title = titleOf(text, fileName, type);
        return new Day21Document(source, fileName, title, type, text);
    }

    private String relativeSource(Path path) {
        Path root = Path.of(properties.corpusDir()).toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        return root.relativize(absolute).toString().replace('\\', '/');
    }

    private static String titleOf(String text, String fileName, String type) {
        if ("markdown".equals(type)) {
            Matcher matcher = FIRST_HEADING.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}