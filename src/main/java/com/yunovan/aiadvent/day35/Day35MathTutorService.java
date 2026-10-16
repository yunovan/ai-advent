package com.yunovan.aiadvent.day35;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class Day35MathTutorService {

    private final Day35Properties properties;
    private final Day35ProblemGenerator generator;
    private final Day35LessonRepository lessons;
    private final Day35Tutor tutor;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public Day35MathTutorService(Day35Properties properties,
                                 Day35ProblemGenerator generator,
                                 Day35LessonRepository lessons,
                                 Day35Tutor tutor) {
        this.properties = properties;
        this.generator = generator;
        this.lessons = lessons;
        this.tutor = tutor;
    }

    public List<Day35Lesson> lessons() {
        return lessons.all();
    }

    public Day35SessionResponse start(Day35SessionRequest request) {
        long started = System.nanoTime();
        String topic = blankTo(request == null ? null : request.topic(), "algebra");
        String level = blankTo(request == null ? null : request.level(), "5-7");
        int count = clamp(request == null ? null : request.count(), properties.maxProblems());

        List<Day35Problem> problems = new ArrayList<>();
        Map<String, String> expected = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Day35ProblemGenerator.Generated generated = generator.generate(topic, level);
            problems.add(generated.problem());
            expected.put(generated.problem().id(), generated.expectedAnswer());
        }

        Session session = new Session(UUID.randomUUID().toString(), topic, level, problems, expected);
        sessions.put(session.id, session);
        long latency = elapsed(started);
        return response(session, session.problems.get(0), null, null, false, false,
                "Сессия начата. AI-тьютор будет разбирать каждое решение по шагам.",
                "", latency, 0, 0, 0.0);
    }

    public Day35SessionResponse answer(String sessionId, Day35SessionRequest request) {
        Session session = sessions.get(sessionId);
        if (session == null) {
            throw new Day35Exception("Сессия не найдена: " + sessionId);
        }
        long started = System.nanoTime();
        if (session.step >= session.problems.size()) {
            return response(session, null, null, null, true, false,
                    "Обучение уже завершено.", "", elapsed(started), 0, 0, 0.0);
        }

        Day35Problem current = session.problems.get(session.step);
        String expectedAnswer = session.expected.get(current.id());
        String userAnswer = request == null ? null : request.userAnswer();
        Day35TutorResult result = tutor.review(current, userAnswer, expectedAnswer);

        session.solved = Math.min(session.problems.size(), session.solved + 1);
        if (result.solution().check().equals("correct")) {
            session.correct++;
        }

        boolean finished = session.step + 1 >= session.problems.size();
        Day35Problem next = finished ? null : session.problems.get(session.step + 1);
        if (!finished) {
            session.step++;
        }
        String note = finished
                ? "Обучение завершено. Посмотрите прогресс и разбор ошибок."
                : "Готово. Разберите объяснение и переходите к следующей задаче.";
        return response(session, next, result.solution(), result.analysis(), finished,
                result.solution().check().equals("correct"), note,
                result.model(), result.latencyMs(), result.promptTokens(),
                result.outputTokens(), result.tokensPerSecond());
    }

    private Day35SessionResponse response(Session session,
                                          Day35Problem problem,
                                          Day35Solution solution,
                                          Day35ErrorAnalysis analysis,
                                          boolean finished,
                                          boolean correct,
                                          String note,
                                          String model,
                                          long latencyMs,
                                          int promptTokens,
                                          int outputTokens,
                                          double tokensPerSecond) {
        int total = session.problems.size();
        String task = finished
                ? "Задача " + total + " из " + total + " (обучение завершено)"
                : "Задача " + (session.step + 1) + " из " + total;
        Day35Progress progress = new Day35Progress(total, session.solved, session.correct,
                List.of(session.topic));
        return new Day35SessionResponse(session.id, task, problem, List.copyOf(session.problems),
                solution, analysis, progress, finished, correct, note,
                model, latencyMs, promptTokens, outputTokens, tokensPerSecond);
    }

    private static int clamp(Integer value, int max) {
        if (value == null || value < 1) {
            return max;
        }
        return Math.min(value, max);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static long elapsed(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static final class Session {
        private final String id;
        private final String topic;
        private final String level;
        private final List<Day35Problem> problems;
        private final Map<String, String> expected;
        private int step;
        private int solved;
        private int correct;

        private Session(String id, String topic, String level,
                        List<Day35Problem> problems, Map<String, String> expected) {
            this.id = id;
            this.topic = topic;
            this.level = level;
            this.problems = problems;
            this.expected = expected;
        }
    }
}
