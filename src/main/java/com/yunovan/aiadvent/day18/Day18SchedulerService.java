package com.yunovan.aiadvent.day18;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day18SchedulerService implements Day18SchedulerApi {

    private static final Logger log = LoggerFactory.getLogger(Day18SchedulerService.class);
    private static final DateTimeFormatter TS = DateTimeFormatter.ISO_INSTANT;

    private final Day18Store store;
    private final Day18Properties properties;
    private final Day18HttpProbe probe;
    private ScheduledExecutorService ticker;

    public Day18SchedulerService(Day18Store store, Day18Properties properties, Day18HttpProbe probe) {
        this.store = store;
        this.properties = properties;
        this.probe = probe;
    }

    @PostConstruct
    public void start() {
        if (ticker != null) {
            return;
        }
        ticker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "day18-scheduler");
            thread.setDaemon(true);
            return thread;
        });
        log.info("День 18: планировщик запущен, тик {} мс", properties.tickMillis());
        ticker.scheduleWithFixedDelay(this::tick, 50, properties.tickMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void stop() {
        if (ticker != null) {
            ticker.shutdownNow();
            ticker = null;
        }
    }

    public void tick() {
        for (Day18Job job : store.listJobs()) {
            if (isDue(job)) {
                run(job);
            }
        }
    }

    boolean isDue(Day18Job job) {
        if ("done".equals(job.getStatus()) || job.getNextRunAt() == null) {
            return false;
        }
        return !parseInstant(job.getNextRunAt()).isAfter(Instant.now());
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    @Override
    public Day18Job addReminder(String topic, Integer delaySeconds) {
        int delay = positiveInt("delaySeconds", delaySeconds);
        String value = topic == null || topic.isBlank() ? "Напоминание" : topic.trim();
        Instant when = Instant.now().plusSeconds(delay);
        String now = TS.format(Instant.now());
        Day18Job job = new Day18Job(
                "j-" + shortId(), "reminder", value, "reminders", "pending",
                delay, null, null, null,
                now, TS.format(when), TS.format(when));
        store.saveJob(job);
        log.info("День 18: запланировано напоминание «{}» на {}", value, when);
        return job;
    }

    @Override
    public Day18Job addCollector(String feed, Integer periodSeconds, String url, String sourceFeed) {
        int period = positiveInt("periodSeconds", periodSeconds);
        String feedValue = feed == null || feed.isBlank() ? "events" : feed.trim();
        Day18Job job = new Day18Job(
                "j-" + shortId(), "collector", feedValue, feedValue, "active",
                null, period, blankToNull(url), blankToNull(sourceFeed),
                TS.format(Instant.now()), null,
                TS.format(Instant.now().plusSeconds(period)));
        store.saveJob(job);
        log.info("День 18: запущен периодический сбор по «{}» каждые {} сек", feedValue, period);
        return job;
    }

    @Override
    public Day18Job runNow(String jobId) {
        Day18Job job = store.findById(jobId == null ? "" : jobId.trim());
        if (job == null) {
            throw new IllegalArgumentException("Задание не найдено: " + jobId);
        }
        run(job);
        return job;
    }

    @Override
    public List<Day18Job> listJobs() {
        return store.listJobs();
    }

    @Override
    public Day18Summary summary(String feed, Integer sinceSeconds) {
        Instant since = sinceSeconds == null || sinceSeconds <= 0
                ? null : Instant.now().minusSeconds(sinceSeconds);
        return Day18Aggregator.aggregate(feed, since, store.allSamples());
    }

    @Override
    public List<Day18Sample> samplesFor(String feed, Integer sinceSeconds) {
        Instant since = sinceSeconds == null || sinceSeconds <= 0
                ? null : Instant.now().minusSeconds(sinceSeconds);
        return store.samplesFor(feed, since);
    }

    private void run(Day18Job job) {
        job.setRunCount(job.getRunCount() == null ? 1 : job.getRunCount() + 1);
        Day18Sample sample;
        if ("reminder".equals(job.getType())) {
            sample = new Day18Sample(
                    "s-" + shortId(), job.getId(), job.getFeed(), "reminder",
                    TS.format(Instant.now()), 1.0, "Напоминание: " + job.getName());
            job.setStatus("done");
            job.setNextRunAt(null);
            job.setLastResult("Напоминание доставлено: " + job.getName());
        } else {
            sample = collect(job);
            job.setStatus("active");
            job.setNextRunAt(TS.format(Instant.now().plusSeconds(job.getPeriodSeconds())));
            job.setLastResult(sample.kind() + ": " + sample.payload());
        }
        store.saveSample(sample);
        store.saveJob(job);
        log.info("День 18: выполнение {} -> {}", job.getId(), sample.payload());
    }

    private Day18Sample collect(Day18Job job) {
        String id = "s-" + shortId();
        String now = TS.format(Instant.now());
        if (job.getSourceFeed() != null && !job.getSourceFeed().isBlank()) {
            Day18Summary summary = Day18Aggregator.aggregate(
                    job.getSourceFeed(), null, store.allSamples());
            return new Day18Sample(id, job.getId(), job.getFeed(), "digest", now,
                    summary.count(), digestText(summary, job.getSourceFeed()));
        }
        if (job.getUrl() != null && !job.getUrl().isBlank()) {
            Day18HttpProbe.ProbeResult result = probe.probe(job.getUrl());
            return new Day18Sample(id, job.getId(), job.getFeed(), "ping", now,
                    result.valueMs(), result.payload());
        }
        return new Day18Sample(id, job.getId(), job.getFeed(), "ping", now,
                job.getRunCount(), "мок-замер №" + job.getRunCount() + " по '" + job.getFeed() + "'");
    }

    private static String digestText(Day18Summary summary, String sourceFeed) {
        StringBuilder text = new StringBuilder();
        text.append("Сводка по '").append(sourceFeed).append("': всего событий ").append(summary.count());
        if (summary.count() > 0) {
            text.append(", первое ").append(shortTs(summary.firstAt()))
                    .append(", последнее ").append(shortTs(summary.lastAt()));
            if (summary.avgValue() != null) {
                text.append(", среднее значение ").append(String.format(Locale.ROOT, "%.1f", summary.avgValue()));
            }
            if (summary.lastPayload() != null) {
                text.append(". Последняя запись: ").append(summary.lastPayload());
            }
        }
        return text.toString();
    }

    private static String shortTs(String iso) {
        if (iso == null) {
            return "—";
        }
        return iso.replace("T", " ").replace("Z", "").substring(0, Math.min(19, iso.length()));
    }

    private static int positiveInt(String key, Integer value) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException("Укажите целое число " + key + " больше нуля");
        }
        return value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }
}