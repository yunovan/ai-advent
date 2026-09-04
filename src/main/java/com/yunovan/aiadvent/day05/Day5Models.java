package com.yunovan.aiadvent.day05;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Day5Models {

    public static final String DEFAULT_PROMPT =
            "Объясни, почему у самолёта крыло имеет профиль (сверху изогнуто), а не плоскую пластину. "
                    + "Затем посчитай 17 × 24. Ответ на 5–7 предложений.";

    public static final String WEAK_MODEL = "meta-llama/llama-3.2-3b-instruct";
    public static final String MEDIUM_MODEL = "qwen/qwen-2.5-7b-instruct";
    public static final String STRONG_MODEL = "meta-llama/llama-3.3-70b-instruct";

    public static final String MODELS_CATALOG_OPENROUTER = "https://openrouter.ai/models";
    public static final String MODELS_CATALOG_HUGGINGFACE = "https://huggingface.co/models?sort=downloads";

    private static final Map<String, String> HUGGINGFACE_CARDS = Map.of(
            WEAK_MODEL, "https://huggingface.co/meta-llama/Llama-3.2-3B-Instruct",
            MEDIUM_MODEL, "https://huggingface.co/Qwen/Qwen2.5-7B-Instruct",
            STRONG_MODEL, "https://huggingface.co/meta-llama/Llama-3.3-70B-Instruct");

    public static final String QUALITY_NOTES =
            """
            Качество: слабая модель чаще короче и ошибается в фактах/арифметике; средняя держит структуру; \
            сильная обычно точнее и полнее.
            Скорость: меньше параметров — быстрее ответ, но очередь провайдера тоже влияет.
            Ресурсоёмкость: больше параметров → больше токенов мысли и выше цена за токен; смотрите cost и total_tokens."""
                    .trim();

    private Day5Models() {
    }

    public static String openRouterUrl(String model) {
        return "https://openrouter.ai/" + model;
    }

    public static String huggingFaceUrl(String model) {
        String known = HUGGINGFACE_CARDS.get(model);
        if (known != null) {
            return known;
        }
        return "https://huggingface.co/models?search=" + URLEncoder.encode(model, StandardCharsets.UTF_8);
    }

    public static String formatCost(BigDecimal costUsd) {
        if (costUsd == null) {
            return "н/д";
        }
        if (costUsd.compareTo(BigDecimal.ZERO) == 0) {
            return "$0";
        }
        BigDecimal scaled = costUsd.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros();
        return "$" + scaled.toPlainString();
    }

    public static String formatTokens(Integer totalTokens) {
        return totalTokens == null ? "н/д" : totalTokens + " tok";
    }

    public static String conclusion(List<ModelRun> runs) {
        if (runs == null || runs.isEmpty()) {
            return QUALITY_NOTES;
        }
        ModelRun fastest = runs.stream().min(Comparator.comparingLong(ModelRun::elapsedMs)).orElse(runs.getFirst());
        ModelRun cheapest = runs.stream()
                .filter(run -> run.costUsd() != null)
                .min(Comparator.comparing(ModelRun::costUsd))
                .orElse(null);
        ModelRun fewestTokens = runs.stream()
                .filter(run -> run.totalTokens() != null)
                .min(Comparator.comparing(ModelRun::totalTokens))
                .orElse(null);

        StringBuilder text = new StringBuilder();
        text.append("Замеры этого запуска:\n");
        for (ModelRun run : runs) {
            text.append("- ")
                    .append(run.label())
                    .append(" (")
                    .append(run.model())
                    .append("): ")
                    .append(run.elapsedMs())
                    .append(" мс, ")
                    .append(formatTokens(run.totalTokens()))
                    .append(", ")
                    .append(formatCost(run.costUsd()))
                    .append('\n');
        }
        text.append("Быстрее всех: ")
                .append(fastest.label())
                .append(String.format(Locale.ROOT, " (%d мс). ", fastest.elapsedMs()));
        if (fewestTokens != null) {
            text.append("Меньше токенов: ")
                    .append(fewestTokens.label())
                    .append(" (")
                    .append(formatTokens(fewestTokens.totalTokens()))
                    .append("). ");
        }
        if (cheapest != null) {
            text.append("Дешевле: ")
                    .append(cheapest.label())
                    .append(" (")
                    .append(formatCost(cheapest.costUsd()))
                    .append("). ");
        }
        text.append('\n').append(QUALITY_NOTES);
        return text.toString().trim();
    }
}
