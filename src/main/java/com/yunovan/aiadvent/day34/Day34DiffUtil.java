package com.yunovan.aiadvent.day34;

import java.util.ArrayList;
import java.util.List;

public final class Day34DiffUtil {

    private static final int CONTEXT = 3;

    private Day34DiffUtil() {
    }

    public static String unified(String path, String oldText, String newText) {
        List<String> before = lines(oldText);
        List<String> after = lines(newText);
        if (before.equals(after)) {
            return "";
        }
        List<Change> changes = diff(before, after);
        String from = oldText == null || oldText.isEmpty() ? "/dev/null" : "a/" + path;
        String to = "b/" + path;
        StringBuilder diff = new StringBuilder();
        diff.append("--- ").append(from).append('\n');
        diff.append("+++ ").append(to).append('\n');
        appendHunks(diff, changes);
        return diff.toString();
    }

    private static void appendHunks(StringBuilder diff, List<Change> changes) {
        int index = 0;
        int size = changes.size();
        while (index < size) {
            if (changes.get(index).op() == Op.EQUAL) {
                index++;
                continue;
            }
            int start = Math.max(0, index - CONTEXT);
            int end = index;
            int gap = 0;
            while (end < size) {
                if (changes.get(end).op() == Op.EQUAL) {
                    gap++;
                    if (gap > CONTEXT * 2) {
                        break;
                    }
                } else {
                    gap = 0;
                }
                end++;
            }
            int hunkEnd = Math.min(size, end);
            while (hunkEnd > start && changes.get(hunkEnd - 1).op() == Op.EQUAL) {
                hunkEnd--;
            }
            hunkEnd = Math.min(size, hunkEnd + CONTEXT);
            writeHunk(diff, changes.subList(start, hunkEnd));
            index = hunkEnd;
        }
    }

    private static void writeHunk(StringBuilder diff, List<Change> hunk) {
        int oldStart = hunk.getFirst().oldLine();
        int newStart = hunk.getFirst().newLine();
        int oldCount = 0;
        int newCount = 0;
        for (Change change : hunk) {
            if (change.op() != Op.ADD) {
                oldCount++;
            }
            if (change.op() != Op.DELETE) {
                newCount++;
            }
        }
        diff.append("@@ -").append(range(oldStart, oldCount))
                .append(" +").append(range(newStart, newCount)).append(" @@\n");
        for (Change change : hunk) {
            diff.append(switch (change.op()) {
                case EQUAL -> " ";
                case DELETE -> "-";
                case ADD -> "+";
            }).append(change.text()).append('\n');
        }
    }

    private static String range(int start, int count) {
        if (count == 0) {
            return start + ",0";
        }
        return count == 1 ? String.valueOf(start) : start + "," + count;
    }

    private static List<Change> diff(List<String> before, List<String> after) {
        int n = before.size();
        int m = after.size();
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (before.get(i).equals(after.get(j))) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }
        List<Change> changes = new ArrayList<>();
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (before.get(i).equals(after.get(j))) {
                changes.add(new Change(Op.EQUAL, before.get(i), i + 1, j + 1));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                changes.add(new Change(Op.DELETE, before.get(i), i + 1, j + 1));
                i++;
            } else {
                changes.add(new Change(Op.ADD, after.get(j), i + 1, j + 1));
                j++;
            }
        }
        while (i < n) {
            changes.add(new Change(Op.DELETE, before.get(i), i + 1, j + 1));
            i++;
        }
        while (j < m) {
            changes.add(new Change(Op.ADD, after.get(j), i + 1, j + 1));
            j++;
        }
        return changes;
    }

    static List<String> lines(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return result;
        }
        for (String line : text.split("\n", -1)) {
            result.add(line);
        }
        if (!result.isEmpty() && result.getLast().isEmpty()) {
            result.removeLast();
        }
        return result;
    }

    private enum Op {
        EQUAL, DELETE, ADD
    }

    private record Change(Op op, String text, int oldLine, int newLine) {
    }
}
