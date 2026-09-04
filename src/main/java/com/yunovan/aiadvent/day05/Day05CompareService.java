package com.yunovan.aiadvent.day05;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Day05CompareService {

    private final LlmClient llmClient;
    private final Day5Properties properties;

    public Day05CompareService(LlmClient llmClient, Day5Properties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public ModelComparisonResponse compare(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        String trimmed = prompt.trim();

        List<ModelRun> runs = new ArrayList<>();
        runs.add(run(ModelTier.WEAK, properties.weakModel(), trimmed));
        runs.add(run(ModelTier.MEDIUM, properties.mediumModel(), trimmed));
        runs.add(run(ModelTier.STRONG, properties.strongModel(), trimmed));

        List<ModelLink> links = List.of(
                new ModelLink("Каталог OpenRouter", Day5Models.MODELS_CATALOG_OPENROUTER),
                new ModelLink("Каталог Hugging Face", Day5Models.MODELS_CATALOG_HUGGINGFACE),
                new ModelLink(runs.get(0).label(), runs.get(0).openRouterUrl()),
                new ModelLink(runs.get(1).label(), runs.get(1).openRouterUrl()),
                new ModelLink(runs.get(2).label(), runs.get(2).openRouterUrl()),
                new ModelLink(runs.get(0).label() + " · HF", runs.get(0).huggingFaceUrl()),
                new ModelLink(runs.get(1).label() + " · HF", runs.get(1).huggingFaceUrl()),
                new ModelLink(runs.get(2).label() + " · HF", runs.get(2).huggingFaceUrl()));

        return new ModelComparisonResponse(trimmed, runs, Day5Models.conclusion(runs), links);
    }

    private ModelRun run(ModelTier tier, String model, String prompt) {
        LlmReply reply = llmClient.complete(CompletionCommand.withModel(prompt, model));
        return ModelRun.from(tier, model, reply);
    }
}
