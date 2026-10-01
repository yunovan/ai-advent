package com.yunovan.aiadvent.day21;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class Day21FixedChunker implements Day21Chunker {

    public static final String STRATEGY = Day21Properties.STRATEGY_FIXED;

    private static final Pattern HEADING_LINE = Pattern.compile(
            "^(?:#{1,6}\\s+.*|(?:public\\s+)?(?:static\\s+)?(?:final\\s+)?"
                    + "(?:class|interface|enum|record)\\s+\\w+).*$",
            Pattern.MULTILINE);

    private record Heading(int offset, String text) {
    }

    private final Day21Properties properties;

    public Day21FixedChunker(Day21Properties properties) {
        this.properties = properties;
    }

    @Override
    public String strategy() {
        return STRATEGY;
    }

    @Override
    public List<Day21Chunk> chunk(Day21Document document) {
        List<Day21Chunk> chunks = new ArrayList<>();
        String text = document.text();
        int size = properties.chunkSize();
        int overlap = properties.chunkOverlap();
        int length = text.length();
        if (length == 0) {
            return chunks;
        }
        List<Heading> headings = scanHeadings(text);
        int headingCursor = 0;
        int index = 0;
        int start = 0;
        while (start < length) {
            int startTrim = start;
            while (startTrim < length && Character.isWhitespace(text.charAt(startTrim))) {
                startTrim++;
            }
            if (startTrim >= length) {
                break;
            }
            int end = startTrim + size;
            if (end >= length) {
                end = length;
            } else {
                int space = text.indexOf(' ', end);
                end = space < 0 ? length : space;
            }
            int endTrim = end;
            while (endTrim > startTrim && Character.isWhitespace(text.charAt(endTrim - 1))) {
                endTrim--;
            }
            if (endTrim <= startTrim) {
                start = end;
                continue;
            }
            if (headingCursor < headings.size()
                    && headings.get(headingCursor).offset() <= startTrim) {
                while (headingCursor < headings.size()
                        && headings.get(headingCursor).offset() <= startTrim) {
                    headingCursor++;
                }
            }
            String section = headingCursor == 0 ? document.title() :
                    headings.get(headingCursor - 1).text();
            index++;
            chunks.add(new Day21Chunk(
                    STRATEGY,
                    document.source(),
                    document.fileName(),
                    document.title(),
                    section,
                    chunkId(document.source(), index),
                    startTrim,
                    endTrim - startTrim,
                    text.substring(startTrim, endTrim)));
            if (endTrim >= length) {
                break;
            }
            int next = endTrim - overlap;
            if (next <= startTrim) {
                next = endTrim;
            }
            int whitespace = text.indexOf(' ', next);
            if (whitespace != -1 && whitespace < next + overlap + 16) {
                next = whitespace + 1;
            }
            if (next <= startTrim) {
                next = endTrim;
            }
            start = next;
        }
        return chunks;
    }

    private static List<Heading> scanHeadings(String text) {
        List<Heading> headings = new ArrayList<>();
        Matcher matcher = HEADING_LINE.matcher(text);
        while (matcher.find()) {
            String line = matcher.group().trim();
            headings.add(new Heading(matcher.start(), stripMarkers(line)));
        }
        return headings;
    }

    private static String stripMarkers(String line) {
        String value = line.replaceFirst("^#{1,6}\\s+", "").trim();
        return value.isEmpty() ? line : value;
    }

    private static String chunkId(String source, int index) {
        return source + "#" + STRATEGY + "#" + String.format("%04d", index);
    }
}