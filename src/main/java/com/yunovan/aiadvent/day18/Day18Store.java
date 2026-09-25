package com.yunovan.aiadvent.day18;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class Day18Store {

    private record Data(List<Day18Job> jobs, List<Day18Sample> samples) {
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;

    public Day18Store(Path storeDir) {
        this.file = storeDir.resolve("scheduler.json");
    }

    public synchronized List<Day18Job> listJobs() {
        return load().jobs();
    }

    public synchronized Day18Job findById(String id) {
        return load().jobs().stream().filter(j -> j.getId().equals(id)).findFirst().orElse(null);
    }

    public synchronized void saveJob(Day18Job job) {
        Data data = load();
        data.jobs().removeIf(j -> j.getId().equals(job.getId()));
        data.jobs().add(job);
        write(data);
    }

    public synchronized void saveSample(Day18Sample sample) {
        Data data = load();
        data.samples().removeIf(s -> s.id().equals(sample.id()));
        data.samples().add(sample);
        write(data);
    }

    public synchronized List<Day18Sample> allSamples() {
        List<Day18Sample> samples = new ArrayList<>(load().samples());
        samples.sort(Comparator.comparing(Day18Sample::collectedAt));
        return List.copyOf(samples);
    }

    public synchronized List<Day18Sample> samplesFor(String feed, Instant since) {
        List<Day18Sample> out = new ArrayList<>();
        for (Day18Sample sample : allSamples()) {
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
            out.add(sample);
        }
        return List.copyOf(out);
    }

    private Data load() {
        if (Files.notExists(file)) {
            return new Data(new ArrayList<>(), new ArrayList<>());
        }
        try {
            return mapper.readValue(file.toFile(), Data.class);
        } catch (IOException ex) {
            return new Data(new ArrayList<>(), new ArrayList<>());
        }
    }

    private void write(Data data) {
        try {
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), data);
        } catch (IOException ex) {
            throw new RuntimeException("Не удалось сохранить данные планировщика: " + ex.getMessage(), ex);
        }
    }
}