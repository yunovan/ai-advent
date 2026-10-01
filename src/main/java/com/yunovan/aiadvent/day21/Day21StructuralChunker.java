package com.yunovan.aiadvent.day21;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class Day21StructuralChunker implements Day21Chunker {

    public static final String STRATEGY = Day21Properties.STRATEGY_STRUCTURAL;

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern CODE_DECLARATION = Pattern.compile(
            "^(?:public\\s+|private\\s+|protected\\s+)?(?:static\\s+)?(?:final\\s+)?"
                    + "(?:class|interface|enum|record)\\s+(\\w+)");
    private static final Pattern BLANK_LINE = Pattern.compile("^\\s*$");

    private record Block(int offset, String section, String text) {
    }

    private final Day21Properties properties;

    public Day21StructuralChunker(Day21Properties properties) {
        this.properties = properties;
    }

    @Override
    public String strategy() {
        return STRATEGY;
    }

    @Override
    public List<Day21Chunk> chunk(Day21Document document) {
        String type = document.type() == null ? "plain" : document.type().toLowerCase(Locale.ROOT);
        List<Block> blocks = switch (type) {
            case "markdown" -> markdownBlocks(document.text(), document.title());
            case "code" -> codeBlocks(document.text());
            default -> plainBlocks(document.text(), document.title());
        };
        return pack(document, blocks);
    }

    private List<Day21Chunk> pack(Day21Document document, List<Block> blocks) {
        List<Day21Chunk> chunks = new ArrayList<>();
        int size = properties.chunkSize();
        List<Block> pending = new ArrayList<>();
        int pendingLength = 0;
        int index = 0;
        for (Block block : blocks) {
            if (block.text().isBlank()) {
                continue;
            }
            if (!pending.isEmpty() && pendingLength + block.text().length() > size) {
                index = emit(document, chunks, pending, index);
                pendingLength = 0;
                pending = new ArrayList<>();
            }
            if (block.text().length() > size) {
                for (Block piece : splitLong(block, size)) {
                    if (!pending.isEmpty() && pendingLength + piece.text().length() > size) {
                        index = emit(document, chunks, pending, index);
                        pendingLength = 0;
                        pending = new ArrayList<>();
                    }
                    pending.add(piece);
                    pendingLength += piece.text().length();
                }
            } else {
                pending.add(block);
                pendingLength += block.text().length();
            }
        }
        if (!pending.isEmpty()) {
            index = emit(document, chunks, pending, index);
        }
        return chunks;
    }

    private int emit(Day21Document document, List<Day21Chunk> chunks, List<Block> pending, int index) {
        StringBuilder text = new StringBuilder();
        StringBuilder section = new StringBuilder();
        int firstOffset = pending.get(0).offset();
        for (Block block : pending) {
            text.append(block.text()).append('\n');
            if (section.isEmpty() && !block.section().isEmpty()) {
                section.append(block.section());
            }
        }
        String content = text.toString().trim();
        index++;
        chunks.add(new Day21Chunk(
                STRATEGY,
                document.source(),
                document.fileName(),
                document.title(),
                section.toString(),
                chunkId(document.source(), index),
                firstOffset,
                content.length(),
                content));
        return index;
    }

    private List<Block> splitLong(Block block, int size) {
        List<Block> pieces = new ArrayList<>();
        String text = block.text();
        int start = 0;
        int pieceIndex = 0;
        while (start < text.length()) {
            int end = start + size;
            if (end >= text.length()) {
                end = text.length();
            } else {
                int space = text.indexOf(' ', end);
                end = space < 0 ? text.length() : space;
            }
            int endTrim = end;
            while (endTrim > start && Character.isWhitespace(text.charAt(endTrim - 1))) {
                endTrim--;
            }
            if (endTrim <= start) {
                endTrim = end;
            }
            String piece = text.substring(start, endTrim).trim();
            if (!piece.isEmpty()) {
                pieces.add(new Block(block.offset() + start, block.section(), piece));
            }
            int next = endTrim;
            if (next <= start) {
                next = end;
            }
            start = next;
            pieceIndex++;
        }
        return pieces;
    }

    private static List<Block> markdownBlocks(String text, String title) {
        List<Block> blocks = new ArrayList<>();
        String section = title == null ? "" : title;
        StringBuilder paragraph = new StringBuilder();
        int paragraphOffset = 0;
        boolean paragraphOpen = false;
        int lineOffset = 0;
        for (String line : text.split("\n", -1)) {
            Matcher heading = MARKDOWN_HEADING.matcher(line);
            if (heading.matches()) {
                if (paragraphOpen) {
                    blocks.add(new Block(paragraphOffset, section, paragraph.toString().trim()));
                    paragraphOpen = false;
                    paragraph.setLength(0);
                }
                section = heading.group(2).trim();
            } else {
                if (!paragraphOpen) {
                    paragraphOffset = lineOffset;
                    paragraphOpen = true;
                }
                if (paragraph.length() > 0 && !BLANK_LINE.matcher(line).matches()) {
                    paragraph.append('\n');
                }
                paragraph.append(line);
                if (BLANK_LINE.matcher(line).matches()) {
                    blocks.add(new Block(paragraphOffset, section, paragraph.toString().trim()));
                    paragraphOpen = false;
                    paragraph.setLength(0);
                }
            }
            lineOffset += line.length() + 1;
        }
        if (paragraphOpen && paragraph.length() > 0) {
            blocks.add(new Block(paragraphOffset, section, paragraph.toString().trim()));
        }
        return blocks;
    }

    private static List<Block> codeBlocks(String text) {
        List<Block> blocks = new ArrayList<>();
        String section = "";
        StringBuilder block = new StringBuilder();
        int blockOffset = 0;
        boolean blockOpen = false;
        int lineOffset = 0;
        for (String line : text.split("\n", -1)) {
            Matcher declaration = CODE_DECLARATION.matcher(line.trim());
            if (declaration.find()) {
                if (blockOpen && block.length() > 0) {
                    blocks.add(new Block(blockOffset, section, block.toString().trim()));
                }
                section = declaration.group(1);
                block = new StringBuilder();
                blockOffset = lineOffset;
                blockOpen = true;
            }
            if (!blockOpen) {
                blockOffset = lineOffset;
                blockOpen = true;
            }
            block.append(line).append('\n');
            lineOffset += line.length() + 1;
        }
        if (blockOpen && block.length() > 0) {
            blocks.add(new Block(blockOffset, section, block.toString().trim()));
        } else if (blocks.isEmpty()) {
            blocks.add(new Block(0, section, text.trim()));
        }
        return blocks;
    }

    private static List<Block> plainBlocks(String text, String title) {
        List<Block> blocks = new ArrayList<>();
        String[] paragraphs = text.split("(?m)^\\s*$");
        int offset = 0;
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!trimmed.isEmpty()) {
                blocks.add(new Block(offset, title == null ? "" : title, trimmed));
            }
            offset += paragraph.length();
        }
        return blocks;
    }

    private static String chunkId(String source, int index) {
        return source + "#" + STRATEGY + "#" + String.format("%04d", index);
    }
}