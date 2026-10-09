package com.yunovan.aiadvent.day30;

import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26InstalledModel;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LoadedModel;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26ModelInfo;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.day29.Day29ModelReport;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day30PrivateLlmService {

    private static final Logger log = LoggerFactory.getLogger(Day30PrivateLlmService.class);

    private static final long RATE_WINDOW_MS = 60_000L;

    private final Day26LocalLlmClient localLlm;
    private final Day26Properties localProperties;
    private final Day30Properties properties;

    private final Map<String, List<Day26ChatMessage>> sessions = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> sessionOrder = new ConcurrentLinkedQueue<>();
    private final SlidingWindowRateLimiter rateLimiter;
    private final Semaphore concurrencyLimit;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong acceptedRequests = new AtomicLong();
    private final AtomicLong rejectedByRate = new AtomicLong();
    private final AtomicLong rejectedByContext = new AtomicLong();
    private final AtomicLong rejectedByConcurrency = new AtomicLong();
    private final AtomicLong latencySum = new AtomicLong();
    private final AtomicLong latencyCount = new AtomicLong();
    private final DoubleAdder tpsSum = new DoubleAdder();
    private final AtomicInteger active = new AtomicInteger();
    private final AtomicInteger peakActive = new AtomicInteger();

    public Day30PrivateLlmService(Day26LocalLlmClient localLlm, Day26Properties localProperties,
                                  Day30Properties properties) {
        this.localLlm = localLlm;
        this.localProperties = localProperties;
        this.properties = properties;
        this.rateLimiter = new SlidingWindowRateLimiter(properties.rateLimitPerMinute(), RATE_WINDOW_MS);
        this.concurrencyLimit = new Semaphore(properties.maxConcurrent());
    }

    public Day30HealthResponse health() {
        Day29ModelReport model = modelReport();
        Day30Limits limits = new Day30Limits(properties.maxMessages(), properties.maxPromptChars(),
                properties.rateLimitPerMinute(), properties.maxConcurrent(), properties.maxTokens(),
                model.contextLength(), properties.apiKeyRequired());
        String status = model.available() && model.installed() ? "ok" : "degraded";
        return new Day30HealthResponse(status, serviceUrl(), properties.host(), properties.port(),
                networkUrls(), model, limits, stats());
    }

    public Day30ChatResponse chat(Day30ChatRequest request, String clientKey, String providedKey) {
        requireAuth(providedKey);
        if (request == null) {
            throw new IllegalArgumentException("Тело запроса обязательно");
        }
        String message = request.message() == null ? "" : request.message().trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }
        totalRequests.incrementAndGet();
        if (message.length() > properties.maxPromptChars()) {
            rejectedByContext.incrementAndGet();
            throw new IllegalArgumentException("Сообщение длиннее лимита контекста ("
                    + properties.maxPromptChars() + " символов)");
        }

        int remaining = rateLimiter.acquire(clientKey == null ? "anonymous" : clientKey);
        if (remaining < 0) {
            rejectedByRate.incrementAndGet();
            throw new Day30RateLimitException("Превышен лимит " + properties.rateLimitPerMinute()
                    + " запросов в минуту для клиента " + clientKey);
        }
        if (!concurrencyLimit.tryAcquire()) {
            rejectedByConcurrency.incrementAndGet();
            throw new Day30RateLimitException("Сервис занят: одновременно обрабатывается не более "
                    + properties.maxConcurrent() + " запросов");
        }

        int nowActive = active.incrementAndGet();
        peakActive.accumulateAndGet(nowActive, Math::max);
        try {
            String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                    ? UUID.randomUUID().toString() : request.sessionId().trim();
            List<Day26ChatMessage> history = sessionHistory(sessionId);
            boolean trimmed = false;
            while (history.size() >= properties.maxMessages()) {
                history.remove(0);
                trimmed = true;
            }
            history.add(new Day26ChatMessage("user", message));
            List<Day26ChatMessage> messages = compose(history);
            int promptChars = chars(messages);
            if (promptChars > properties.maxPromptChars()) {
                rejectedByContext.incrementAndGet();
                throw new IllegalArgumentException("Контекст диалога превышает лимит "
                        + properties.maxPromptChars() + " символов (сейчас " + promptChars + ")");
            }

            Day26Answer answer = localLlm.chat(messages,
                    new Day26ChatOptions(properties.temperature(), properties.maxTokens(), null));
            history.add(new Day26ChatMessage("assistant", answer.reply()));
            storeSession(sessionId, history);

            int turn = (int) history.stream().filter(item -> "user".equals(item.role())).count();
            acceptedRequests.incrementAndGet();
            latencySum.addAndGet(answer.latencyMs());
            latencyCount.incrementAndGet();
            tpsSum.add(answer.tokensPerSecond());
            return new Day30ChatResponse(sessionId, turn, answer.reply(), messages.size(),
                    promptChars, trimmed, answer.latencyMs(), answer.promptTokens(),
                    answer.outputTokens(), answer.tokensPerSecond(), remaining, answer.model());
        } finally {
            active.decrementAndGet();
            concurrencyLimit.release();
        }
    }

    public Day30StressResponse stress(Day30StressRequest request, String clientKey, String providedKey) {
        requireAuth(providedKey);
        int requests = request == null || request.requests() == null || request.requests() <= 0
                ? properties.stressRequests() : Math.min(request.requests(), 100);
        int concurrency = request == null || request.concurrency() == null || request.concurrency() <= 0
                ? properties.stressConcurrency() : Math.min(request.concurrency(), 16);

        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        long started = System.nanoTime();
        List<Future<Day30StressItem>> futures = new ArrayList<>();
        for (int index = 1; index <= requests; index++) {
            final int itemIndex = index;
            futures.add(pool.submit(() -> runStressItem(itemIndex, clientKey, providedKey)));
        }
        List<Day30StressItem> items = new ArrayList<>();
        for (Future<Day30StressItem> future : futures) {
            try {
                items.add(future.get());
            } catch (Exception ex) {
                items.add(new Day30StressItem(items.size() + 1, false, false, 0, 0,
                        ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            }
        }
        pool.shutdown();
        long totalMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);

        int succeeded = (int) items.stream().filter(Day30StressItem::ok).count();
        int rateLimited = (int) items.stream().filter(Day30StressItem::rateLimited).count();
        int failed = items.size() - succeeded - rateLimited;
        List<Long> latencies = items.stream().filter(Day30StressItem::ok)
                .map(Day30StressItem::latencyMs).toList();
        double avg = latencies.isEmpty() ? 0
                : latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long min = latencies.isEmpty() ? 0 : Collections.min(latencies);
        long max = latencies.isEmpty() ? 0 : Collections.max(latencies);
        double throughput = totalMs == 0 ? 0
                : Math.round(succeeded * 1000.0 / totalMs * 10.0) / 10.0;
        return new Day30StressResponse(requests, concurrency, succeeded, failed, rateLimited, totalMs,
                throughput, avg, min, max, items,
                stressVerdict(requests, succeeded, failed, rateLimited));
    }

    private Day30StressItem runStressItem(int index, String clientKey, String providedKey) {
        long started = System.nanoTime();
        try {
            Day30ChatResponse answer = chat(new Day30ChatRequest("stress-" + index, promptFor(index)),
                    clientKey, providedKey);
            long latency = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            return new Day30StressItem(index, true, false, latency, answer.outputTokens(), "");
        } catch (Day30RateLimitException ex) {
            long latency = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            return new Day30StressItem(index, false, true, latency, 0, ex.getMessage());
        } catch (RuntimeException ex) {
            long latency = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            return new Day30StressItem(index, false, false, latency, 0, message(ex));
        }
    }

    private void requireAuth(String providedKey) {
        if (!properties.apiKeyRequired()) {
            return;
        }
        if (providedKey == null || !properties.apiKey().equals(providedKey.trim())) {
            throw new Day30AuthException("Неверный или отсутствующий ключ доступа (X-Api-Key)");
        }
    }

    private List<Day26ChatMessage> sessionHistory(String sessionId) {
        List<Day26ChatMessage> existing = sessions.get(sessionId);
        return existing == null ? new ArrayList<>() : new ArrayList<>(existing);
    }

    private void storeSession(String sessionId, List<Day26ChatMessage> history) {
        int size = history.size();
        List<Day26ChatMessage> bounded = size > properties.maxMessages()
                ? new ArrayList<>(history.subList(size - properties.maxMessages(), size))
                : new ArrayList<>(history);
        boolean isNew = !sessions.containsKey(sessionId);
        sessions.put(sessionId, bounded);
        if (isNew) {
            sessionOrder.add(sessionId);
            while (sessions.size() > properties.maxSessions()) {
                String oldest = sessionOrder.poll();
                if (oldest == null) {
                    break;
                }
                sessions.remove(oldest);
            }
        }
    }

    private List<Day26ChatMessage> compose(List<Day26ChatMessage> history) {
        List<Day26ChatMessage> messages = new ArrayList<>();
        if (properties.systemPrompt() != null && !properties.systemPrompt().isBlank()) {
            messages.add(new Day26ChatMessage("system", properties.systemPrompt()));
        }
        messages.addAll(history);
        return messages;
    }

    private static int chars(List<Day26ChatMessage> messages) {
        int total = 0;
        for (Day26ChatMessage message : messages) {
            total += message.content() == null ? 0 : message.content().length();
        }
        return total;
    }

    private Day30Stats stats() {
        long count = latencyCount.get();
        Double avgLatency = count == 0 ? null : (double) latencySum.get() / count;
        Double avgTps = count == 0 ? null : tpsSum.sum() / count;
        return new Day30Stats(totalRequests.get(), acceptedRequests.get(), rejectedByRate.get(),
                rejectedByContext.get(), rejectedByConcurrency.get(), active.get(),
                peakActive.get(), sessions.size(), avgLatency, avgTps);
    }

    private Day29ModelReport modelReport() {
        String endpoint = localProperties.endpoint();
        String model = localProperties.model();
        try {
            String version = localLlm.version();
            Day26InstalledModel installed = localLlm.models().stream()
                    .filter(item -> matches(item.name(), model))
                    .findFirst()
                    .orElse(null);
            Day26ModelInfo info = localLlm.modelInfo();
            Day26LoadedModel loaded = localLlm.loadedModel();
            String reason = installed == null ? "модель " + model + " не установлена в Ollama" : "";
            return new Day29ModelReport(endpoint, model, true, installed != null, version,
                    info.format(), info.parameterSize(), info.quantizationLevel(),
                    info.parameterCount(), info.contextLength(),
                    installed == null ? 0 : installed.sizeBytes(),
                    loaded == null ? 0 : loaded.sizeBytes(),
                    reason.isEmpty() ? info.reason() : reason);
        } catch (Day26LlmException ex) {
            return Day29ModelReport.unavailable(endpoint, model, ex.getMessage());
        } catch (RuntimeException ex) {
            return Day29ModelReport.unavailable(endpoint, model,
                    "не удалось проверить локальную LLM: " + message(ex));
        }
    }

    private String serviceUrl() {
        return "http://" + properties.host() + ":" + properties.port();
    }

    private List<String> networkUrls() {
        List<String> urls = new ArrayList<>();
        urls.add(serviceUrl());
        try {
            var interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(iface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        urls.add("http://" + address.getHostAddress() + ":" + properties.port());
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Не удалось определить сетевые адреса сервиса: {}", ex.getMessage());
        }
        return urls;
    }

    private static String promptFor(int index) {
        String[] prompts = {
            "Привет! Ответь одним предложением, кто ты и где работаешь.",
            "Назови три вещи, которые нужны для локального AI-сервиса.",
            "Что такое rate limit, коротко?",
            "Зачем ограничивать размер контекста у локальной модели?",
            "В чём плюс приватного AI-сервиса на своём сервере?",
            "Как проверить стабильность сервиса под нагрузкой?"
        };
        return prompts[(index - 1) % prompts.length];
    }

    private static String stressVerdict(int requests, int succeeded, int failed, int rateLimited) {
        StringBuilder text = new StringBuilder("Одновременных запросов: ").append(requests)
                .append(", успешно ").append(succeeded);
        if (rateLimited > 0) {
            text.append(", отклонено лимитом ").append(rateLimited);
        }
        if (failed > 0) {
            text.append(", ошибок ").append(failed);
        }
        text.append(". ");
        if (succeeded == requests) {
            text.append("Сервис выдержал нагрузку без потерь.");
        } else if (failed == 0 && succeeded > 0) {
            text.append("Сервис отвечал стабильно, часть запросов ограничена rate limit.");
        } else {
            text.append("Не все запросы обработаны — изучите ошибки и лимиты.");
        }
        return text.toString();
    }

    private static boolean matches(String installed, String expected) {
        if (installed == null || expected == null) {
            return false;
        }
        return installed.trim().toLowerCase().startsWith(expected.trim().toLowerCase());
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static final class SlidingWindowRateLimiter {

        private final int limit;
        private final long windowMs;
        private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

        SlidingWindowRateLimiter(int limit, long windowMs) {
            this.limit = limit;
            this.windowMs = windowMs;
        }

        synchronized int acquire(String key) {
            long now = System.currentTimeMillis();
            Deque<Long> deque = hits.computeIfAbsent(key, ignored -> new ArrayDeque<>());
            while (!deque.isEmpty() && now - deque.peekFirst() > windowMs) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return -1;
            }
            deque.addLast(now);
            return limit - deque.size();
        }
    }
}
