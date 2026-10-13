package com.yunovan.aiadvent.day32;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Day32DiffParser {

    private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/(.+) b/(.+)$");
    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -\\d+(?:,\\d+)? \\+(\\d+)(?:,\\d+)? @@");

    private Day32DiffParser() {
    }

    public static List<Day32FileDiff> parse(String diff) {
        if (diff == null || diff.isBlank()) {
            return List.of();
        }
        List<Day32FileDiff> files = new ArrayList<>();
        Builder current = null;
        int newLine = 0;
        boolean inHunks = false;
        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("diff --git ")) {
                if (current != null) {
                    files.add(current.build());
                }
                current = new Builder(line);
                inHunks = false;
                continue;
            }
            if (current == null) {
                continue;
            }
            if (line.startsWith("new file mode")) {
                current.status = "added";
            } else if (line.startsWith("deleted file mode")) {
                current.status = "deleted";
            } else if (line.startsWith("rename from ")) {
                current.oldPath = line.substring("rename from ".length()).trim();
                current.status = "renamed";
            } else if (line.startsWith("rename to ")) {
                current.path = line.substring("rename to ".length()).trim();
                current.status = "renamed";
            } else if (line.startsWith("--- ")) {
                String path = stripPrefix(line.substring(4).trim());
                if (!"/dev/null".equals(path) && current.oldPath == null) {
                    current.oldPath = path;
                }
            } else if (line.startsWith("+++ ")) {
                String path = stripPrefix(line.substring(4).trim());
                if (!"/dev/null".equals(path)) {
                    current.path = path;
                }
            } else if (line.startsWith("@@")) {
                inHunks = true;
                current.hunks++;
                Matcher matcher = HUNK_HEADER.matcher(line);
                if (matcher.find()) {
                    newLine = Integer.parseInt(matcher.group(1));
                }
            } else if (inHunks) {
                if (line.startsWith("+")) {
                    current.additions++;
                    current.addedLines.add(new Day32Line(newLine, line.substring(1)));
                    newLine++;
                } else if (line.startsWith("-")) {
                    current.deletions++;
                } else if (!line.startsWith("\\")) {
                    newLine++;
                }
            }
        }
        if (current != null) {
            files.add(current.build());
        }
        return files;
    }

    private static String stripPrefix(String path) {
        if (path.startsWith("a/") || path.startsWith("b/")) {
            return path.substring(2);
        }
        return path;
    }

    private static final class Builder {
        private String path;
        private String oldPath;
        private String status = "modified";
        private int additions;
        private int deletions;
        private int hunks;
        private final List<Day32Line> addedLines = new ArrayList<>();

        private Builder(String headerLine) {
            Matcher matcher = DIFF_HEADER.matcher(headerLine);
            if (matcher.matches()) {
                oldPath = matcher.group(1);
                path = matcher.group(2);
            } else {
                path = headerLine;
            }
        }

        private Day32FileDiff build() {
            return new Day32FileDiff(path, oldPath == null || oldPath.equals(path) ? "" : oldPath,
                    status, additions, deletions, hunks, List.copyOf(addedLines));
        }
    }
}
