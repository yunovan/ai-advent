package com.yunovan.aiadvent.day21;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

public final class Day21PdfText {

    private static final Pattern STREAM_BLOCK = Pattern.compile("(?s)stream\\s(.*?)endstream");
    private static final Pattern BT_BLOCK = Pattern.compile("(?s)BT(.*?)ET");
    private static final Pattern TJ_STRING =
            Pattern.compile("\\(((?:\\\\.|[^\\\\()])*)\\)\\s*Tj");
    private static final Pattern TJ_ARRAY =
            Pattern.compile("\\[((?:\\(\\\\?[^\\]]*\\)|[\\d\\s\\-])*)]\\s*TJ");

    private Day21PdfText() {
    }

    public static String extract(byte[] pdf) {
        String raw = new String(pdf, StandardCharsets.ISO_8859_1);
        Matcher streamMatcher = STREAM_BLOCK.matcher(raw);
        List<String> lines = new ArrayList<>();
        while (streamMatcher.find()) {
            String block = streamMatcher.group(1);
            boolean flate = hasFlate(raw, streamMatcher.start());
            byte[] content = decode(block, flate);
            if (content == null) {
                continue;
            }
            collectText(content, lines);
        }
        return String.join(" ", lines).trim();
    }

    private static boolean hasFlate(String raw, int streamStart) {
        int from = Math.max(0, streamStart - 4096);
        return raw.substring(from, streamStart).contains("FlateDecode");
    }

    private static byte[] decode(String block, boolean flate) {
        try {
            byte[] bytes = block.getBytes(StandardCharsets.ISO_8859_1);
            if (!flate) {
                return bytes;
            }
            Inflater inflater = new Inflater();
            inflater.setInput(bytes);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            while (!inflater.finished() && !inflater.needsInput()) {
                int read = inflater.inflate(buffer);
                if (read == 0) {
                    break;
                }
                out.write(buffer, 0, read);
            }
            inflater.end();
            return out.toByteArray();
        } catch (Exception ex) {
            return null;
        }
    }

    private static void collectText(byte[] content, List<String> lines) {
        String text = new String(content, StandardCharsets.ISO_8859_1);
        Matcher btMatcher = BT_BLOCK.matcher(text);
        while (btMatcher.find()) {
            String block = btMatcher.group(1);
            Matcher arrayMatcher = TJ_ARRAY.matcher(block);
            if (arrayMatcher.find()) {
                Matcher element = Pattern.compile("\\(((?:\\\\.|[^\\\\()])*)\\)")
                        .matcher(arrayMatcher.group(1));
                while (element.find()) {
                    lines.add(unEscape(element.group(1)));
                }
                continue;
            }
            Matcher tjMatcher = TJ_STRING.matcher(block);
            while (tjMatcher.find()) {
                lines.add(unEscape(tjMatcher.group(1)));
            }
        }
    }

    private static String unEscape(String value) {
        return value
                .replace("\\(", "(")
                .replace("\\)", ")")
                .replace("\\\\", "\\")
                .replace("\\n", " ");
    }
}