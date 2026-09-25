package com.yunovan.aiadvent.day18;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public final class Day18Aggregator {

    private static final Pattern STATUS_OK = Pattern.compile("status=(2\\d\\d)");

    private Day18Aggregator() {
    }

    public static Day18Summary aggregate(String feed, Instant since, List<Day18Sample> all) {
        List<Day18Sample> matching = new ArrayList<>();
        for (Day18Sample sample : all) {
            if (feed != null && !feed.isBlank() && !feed.equals(sample.feed())) {
                continue;
            }
            if (since != null) {
                try {
                    if (Instant.parse(sample.collectedAt()).isBefore(since)) {
                        continue;
                    }
                } catch (Exception ex) {
                    continue;
                }
            }
            matching.add(sample);
        }
        matching.sort(Comparator.comparing(Day18Sample::collectedAt));

        if (matching.isEmpty()) {
            return new Day18Summary(feed, since == null ? null : since.toString(),
                    0, null, null, null, null, null, null, null, List.of());
        }

        String firstAt = matching.get(0).collectedAt();
        String lastAt = matching.get(matching.size() - 1).collectedAt();
        double sum = 0;
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;
        List<String> statusPayloads = new ArrayList<>();
        for (Day18Sample sample : matching) {
            sum += sample.value();
            if (sample.value() < min) {
                min = sample.value();
            }
            if (sample.value() > max) {
                max = sample.value();
            }
            if (sample.payload() != null && sample.payload().contains("status=")) {
                statusPayloads.add(sample.payload());
            }
        }
        Double successRate = null;
        if (!statusPayloads.isEmpty()) {
            long ok = statusPayloads.stream().filter(p -> STATUS_OK.matcher(p).find()).count();
            successRate = (double) ok / statusPayloads.size();
        }
        List<Day18Sample> recent = List.copyOf(
                matching.subList(Math.max(0, matching.size() - 5), matching.size()));
        return new Day18Summary(feed, since == null ? null : since.toString(),
                matching.size(), firstAt, lastAt,
                sum / matching.size(), min, max, successRate,
                matching.get(matching.size() - 1).payload(), recent);
    }
}