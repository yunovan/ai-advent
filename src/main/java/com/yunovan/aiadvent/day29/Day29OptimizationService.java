package com.yunovan.aiadvent.day29;

import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day22.Day22ControlQuestion;
import com.yunovan.aiadvent.day22.Day22ControlQuestions;
import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26InstalledModel;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LoadedModel;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26ModelInfo;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.day28.Day28RagService;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day29OptimizationService {

    private static final Logger log = LoggerFactory.getLogger(Day29OptimizationService.class);

    private static final String PROFILE_BASELINE = "baseline";
    private static final String PROFILE_TUNED = "tuned";
    private static final int TUNED_CHUNK_CHARS = 560;

    private static final String TUNED_SYSTEM =
            "Отвечай по-русски строго по переданному контексту проекта AI Advent. "
                    + "Кратко и по существу (3-6 предложений), без вступлений, перечисли все "
                    + "релевантные факты из контекста. Для каждого факта указывай источник (файл). "
                    + "Если в контексте нет ответа — ответь «нет данных в контексте». "
                    + "Не выдумывай факты и не используй информацию из памяти.";

    private final Day28RagService rag;
    private final Day26LocalLlmClient localLlm;
    private final Day26Properties localProperties;
    private final Day29Properties properties;
    private final DecimalFormat one;

    public Day29OptimizationService(Day28RagService rag, Day26LocalLlmClient localLlm,
                                    Day26Properties localProperties, Day29Properties properties) {
        this.rag = rag;
        this.localLlm = localLlm;
        this.localProperties = localProperties;
        this.properties = properties;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        this.one = new DecimalFormat("0.0", symbols);
    }

    public Day29HealthResponse health() {
        return new Day29HealthResponse(modelReport(), baselineProfile(), tunedProfile(),
                properties.benchmarkRuns(), properties.questionsLimit(),
                Day28RagService.RETRIEVAL_NOTE);
    }

    public Day29AskResponse ask(String question) {
        String questionText = Day28RagService.requireQuestion(question);
        Day28RagService.Pipeline pipeline = rag.retrieve(questionText);
        Day29ProfileInfo baseline = baselineProfile();
        Day29ProfileInfo tuned = tunedProfile();
        String baselinePrompt = rag.prompt(questionText, pipeline);
        String tunedPrompt = tunedPrompt(questionText, pipeline);
        Day29AnswerResponse baselineAnswer = answer(baseline, questionText, pipeline, baselinePrompt);
        Day29AnswerResponse tunedAnswer = answer(tuned, questionText, pipeline, tunedPrompt);
        return new Day29AskResponse(questionText, Day28RagService.sources(pipeline.hits()),
                baselinePrompt.length(), tunedPrompt.length(),
                baselineAnswer, tunedAnswer,
                askQualityVerdict(baselineAnswer, tunedAnswer),
                askSpeedVerdict(baselineAnswer, tunedAnswer));
    }

    public Day29RunResponse run() {
        int runs = properties.benchmarkRuns();
        List<Day22ControlQuestion> questions = Day22ControlQuestions.ALL.stream()
                .limit(properties.questionsLimit()).toList();
        List<Day29AnswerResponse> baselineAnswers = new ArrayList<>();
        List<Day29AnswerResponse> tunedAnswers = new ArrayList<>();
        List<Double> baselineCoverages = new ArrayList<>();
        List<Double> tunedCoverages = new ArrayList<>();

        for (Day22ControlQuestion question : questions) {
            Day28RagService.Pipeline pipeline = rag.retrieve(question.question());
            Day29ProfileInfo baseline = baselineProfile();
            Day29ProfileInfo tuned = tunedProfile();
            for (int run = 0; run < runs; run++) {
                Day29AnswerResponse baselineAnswer = answer(baseline, question.question(), pipeline,
                        rag.prompt(question.question(), pipeline));
                baselineAnswers.add(baselineAnswer);
                baselineCoverages.add(100.0 * Day28RagService.keywordCoverage(
                        question.expectedKeywords(), baselineAnswer.answer()));
                Day29AnswerResponse tunedAnswer = answer(tuned, question.question(), pipeline,
                        tunedPrompt(question.question(), pipeline));
                tunedAnswers.add(tunedAnswer);
                tunedCoverages.add(100.0 * Day28RagService.keywordCoverage(
                        question.expectedKeywords(), tunedAnswer.answer()));
            }
        }

        Day29ProfileStats baselineStats = stats(baselineProfile(), baselineAnswers, baselineCoverages);
        Day29ProfileStats tunedStats = stats(tunedProfile(), tunedAnswers, tunedCoverages);
        long memory = loadedMemoryBytes();
        return new Day29RunResponse(questions.size(), runs, baselineStats, tunedStats, memory,
                qualityVerdict(baselineStats, tunedStats),
                speedVerdict(baselineStats, tunedStats),
                resourceVerdict(baselineStats, tunedStats, memory));
    }

    private Day29AnswerResponse answer(Day29ProfileInfo profile, String question,
                                       Day28RagService.Pipeline pipeline, String userPrompt) {
        String system = profile.tuned() ? TUNED_SYSTEM : Day28RagService.SYSTEM;
        Day26ChatOptions options = new Day26ChatOptions(
                profile.temperature(), profile.maxTokens(), profile.numCtx());
        List<Day26ChatMessage> messages = List.of(
                new Day26ChatMessage("system", system),
                new Day26ChatMessage("user", userPrompt));
        long started = System.nanoTime();
        try {
            Day26Answer answer = localLlm.chat(messages, options);
            String content = answer.reply();
            return new Day29AnswerResponse(profile.id(), profile.title(), question, content,
                    false, Day28RagService.grounding(content, pipeline),
                    Day28RagService.sources(pipeline.hits()), pipeline.matchedQuery(),
                    answer.latencyMs(), answer.promptTokens(), answer.outputTokens(),
                    answer.tokensPerSecond(), "");
        } catch (Day26LlmException ex) {
            long elapsedMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            log.info("День 29: профиль {} не ответил, используется сборка из индекса: {}",
                    profile.id(), ex.getMessage());
            String content = Day28RagService.fallbackAnswer(question, pipeline);
            return new Day29AnswerResponse(profile.id(), profile.title(), question, content,
                    true, Day28RagService.grounding(content, pipeline),
                    Day28RagService.sources(pipeline.hits()), pipeline.matchedQuery(),
                    elapsedMs, 0, 0, 0.0, ex.getMessage());
        }
    }

    private Day29ProfileInfo baselineProfile() {
        return new Day29ProfileInfo(PROFILE_BASELINE, "Базовый (день 28)",
                localProperties.temperature(), localProperties.maxTokens(), null, "day28",
                "промпт и параметры дня 28: temperature "
                        + localProperties.temperature() + ", max_tokens "
                        + localProperties.maxTokens() + ", контекст по умолчанию Ollama");
    }

    private Day29ProfileInfo tunedProfile() {
        return new Day29ProfileInfo(PROFILE_TUNED, "Оптимизированный",
                properties.tunedTemperature(), properties.tunedNumPredict(),
                properties.tunedNumCtx(), "compact-rag",
                "компактный RAG-шаблон: temperature " + properties.tunedTemperature()
                        + ", num_ctx " + properties.tunedNumCtx() + ", num_predict "
                        + properties.tunedNumPredict() + ", чанк обрезается до "
                        + TUNED_CHUNK_CHARS + " символов");
    }

    private Day29ModelReport modelReport() {
        String endpoint = localProperties.endpoint();
        String model = localProperties.model();
        try {
            String version = localLlm.version();
            Day26InstalledModel installed = localLlm.models().stream()
                    .filter(m -> matches(m.name(), model))
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

    private static String tunedPrompt(String question, Day28RagService.Pipeline pipeline) {
        StringBuilder prompt = new StringBuilder("Контекст из индекса:\n");
        if (pipeline.hits().isEmpty()) {
            prompt.append("ничего не найдено.\n");
        }
        for (Day21SearchHit hit : pipeline.hits()) {
            prompt.append('[').append(hit.chunk().source())
                    .append(", «").append(Day28RagService.section(hit)).append("»]: ")
                    .append(compact(hit.chunk().text(), TUNED_CHUNK_CHARS)).append('\n');
        }
        prompt.append("\nВопрос: ").append(question).append('\n');
        prompt.append("Правила: отвечай только по контексту, укажи файл-источник, "
                + "если ответа нет — «нет данных в контексте».");
        return prompt.toString();
    }

    private static String compact(String text, int limit) {
        if (text == null || text.length() <= limit) {
            return text == null ? "" : text;
        }
        return text.substring(0, limit).stripTrailing() + "…";
    }

    private Day29ProfileStats stats(Day29ProfileInfo profile, List<Day29AnswerResponse> answers,
                                    List<Double> coverages) {
        if (answers.isEmpty()) {
            return new Day29ProfileStats(profile.id(), profile.title(), 0, 0,
                    null, null, null, null, null, null);
        }
        int fallbacks = (int) answers.stream().filter(Day29AnswerResponse::fallback).count();
        List<Double> groundings = answers.stream()
                .map(Day29AnswerResponse::groundingPercent)
                .filter(value -> value != null)
                .toList();
        return new Day29ProfileStats(profile.id(), profile.title(), answers.size(), fallbacks,
                average(coverages), average(groundings),
                average(answers.stream().map(a -> (double) a.latencyMs()).toList()),
                average(answers.stream().map(Day29AnswerResponse::tokensPerSecond).filter(v -> v > 0).toList()),
                average(answers.stream().map(a -> (double) a.promptTokens()).toList()),
                average(answers.stream().map(a -> (double) a.outputTokens()).toList()));
    }

    private String askQualityVerdict(Day29AnswerResponse baseline, Day29AnswerResponse tuned) {
        String quality;
        Double baselineGrounding = baseline.groundingPercent();
        Double tunedGrounding = tuned.groundingPercent();
        if (baselineGrounding == null || tunedGrounding == null) {
            quality = "Опира на контекст не измерялась — поисковый запрос не дал хитов.";
        } else {
            double diff = tunedGrounding - baselineGrounding;
            quality = Math.abs(diff) < 0.05
                    ? "Качество одинаковое: опора на контекст "
                            + one.format(baselineGrounding) + "% в обоих профилях."
                    : (diff > 0 ? "Качество: оптимизированный профиль опирается на контекст сильнее ("
                            + one.format(tunedGrounding) + "% против "
                            + one.format(baselineGrounding) + "%)."
                            : "Качество: базовый профиль опирается на контекст сильнее ("
                            + one.format(baselineGrounding) + "% против "
                            + one.format(tunedGrounding) + "%).");
        }
        return quality;
    }

    private static String askSpeedVerdict(Day29AnswerResponse baseline, Day29AnswerResponse tuned) {
        long diff = tuned.latencyMs() - baseline.latencyMs();
        if (Math.abs(diff) < 500) {
            return "Скорость одинаковая: разница " + Math.abs(diff) + " мс.";
        }
        return diff < 0
                ? "Скорость: оптимизированный профиль быстрее на " + (-diff) + " мс."
                : "Скорость: оптимизированный профиль медленнее на " + diff + " мс.";
    }

    private String qualityVerdict(Day29ProfileStats baseline, Day29ProfileStats tuned) {
        if (baseline.avgCoveragePercent() == null || tuned.avgCoveragePercent() == null) {
            return "Качество не измерялось — нет ответов для сравнения.";
        }
        double diff = tuned.avgCoveragePercent() - baseline.avgCoveragePercent();
        StringBuilder text = new StringBuilder("Качество (покрытие ключевых слов): базовый ")
                .append(one.format(baseline.avgCoveragePercent())).append("% против оптимизированного ")
                .append(one.format(tuned.avgCoveragePercent())).append("% — разница ")
                .append(one.format(Math.abs(diff))).append(" п.п. ");
        if (Math.abs(diff) < 1.0) {
            text.append("Оптимизация не ухудшила качество ответов.");
        } else if (diff > 0) {
            text.append("Оптимизированный профиль отвечает точнее.");
        } else {
            text.append("Компактный шаблон немного снизил полноту ответов — "
                    + "это плата за скорость, оцените в видео на примере вопросов.");
        }
        return text.toString();
    }

    private String speedVerdict(Day29ProfileStats baseline, Day29ProfileStats tuned) {
        if (baseline.avgLatencyMs() == null || tuned.avgLatencyMs() == null) {
            return "Скорость не измерялась — нет ответов для сравнения.";
        }
        double diff = tuned.avgLatencyMs() - baseline.avgLatencyMs();
        double percent = baseline.avgLatencyMs() <= 0 ? 0
                : 100.0 * diff / baseline.avgLatencyMs();
        StringBuilder text = new StringBuilder("Скорость: базовый профиль ");
        text.append(one.format(baseline.avgLatencyMs())).append(" мс в среднем, оптимизированный ")
                .append(one.format(tuned.avgLatencyMs())).append(" мс — разница ")
                .append(one.format(Math.abs(diff))).append(" мс (")
                .append(one.format(Math.abs(percent))).append("%). ");
        if (Math.abs(percent) < 5.0) {
            text.append("Разница в пределах шума.");
        } else if (diff < 0) {
            text.append("Оптимизация ускорила ответ: короче промпт и меньше токенов на выход.");
        } else {
            text.append("Оптимизация не ускорила ответ в этих прогонах.");
        }
        return text.toString();
    }

    private String resourceVerdict(Day29ProfileStats baseline, Day29ProfileStats tuned, long memory) {
        if (baseline.avgOutputTokens() == null || tuned.avgOutputTokens() == null) {
            return "Потребление не измерялось — нет ответов для сравнения.";
        }
        double inputDiff = baseline.avgInputTokens() - tuned.avgInputTokens();
        double outputDiff = baseline.avgOutputTokens() - tuned.avgOutputTokens();
        StringBuilder text = new StringBuilder("Ресурсы: входных токенов на запрос — базовый ")
                .append(one.format(baseline.avgInputTokens())).append(" против оптимизированного ")
                .append(one.format(tuned.avgInputTokens()))
                .append(", на выходе — ").append(one.format(baseline.avgOutputTokens()))
                .append(" против ").append(one.format(tuned.avgOutputTokens())).append(". ");
        if (inputDiff > 0 || outputDiff > 0) {
            text.append("Оптимизированный профиль экономит ")
                    .append(one.format(Math.max(inputDiff, 0) + Math.max(outputDiff, 0)))
                    .append(" токенов на запрос");
            text.append(memory > 0
                    ? ", модель занимает в памяти " + one.format(memory / 1_048_576.0) + " МБ."
                    : ".");
        } else {
            text.append(memory > 0
                    ? "Модель занимает в памяти " + one.format(memory / 1_048_576.0) + " МБ."
                    : "");
        }
        return text.toString();
    }

    private long loadedMemoryBytes() {
        try {
            Day26LoadedModel loaded = localLlm.loadedModel();
            return loaded == null ? 0 : loaded.sizeBytes();
        } catch (Day26LlmException ex) {
            return 0;
        }
    }

    private static boolean matches(String installedName, String model) {
        if (installedName == null || model == null) {
            return false;
        }
        return installedName.equals(model) || installedName.startsWith(model + ":");
    }

    private static Double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        double sum = 0;
        for (Double value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
