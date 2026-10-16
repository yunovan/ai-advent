package com.yunovan.aiadvent.day34;

import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Day34FileService {

    private final Day34Properties properties;
    private final Day34Workspace workspace;
    private final Day34McpClient client;
    private final Day34FileAgent agent;
    private final Day26LocalLlmClient localLlm;
    private final Day26Properties localProperties;

    public Day34FileService(Day34Properties properties, Day34Workspace workspace,
            Day34McpClient client, Day34FileAgent agent, Day26LocalLlmClient localLlm,
            Day26Properties localProperties) {
        this.properties = properties;
        this.workspace = workspace;
        this.client = client;
        this.agent = agent;
        this.localLlm = localLlm;
        this.localProperties = localProperties;
    }

    public Day34HealthResponse health() {
        boolean available = workspace.available();
        List<Day34FileInfo> codeFiles = workspace.list(properties.codeDir(), properties.maxFiles());
        int documents = workspace.list(properties.docsDir(), properties.maxFiles()).size();
        if (workspace.exists(properties.readmePath())) {
            documents++;
        }
        long corpusChars = codeFiles.stream().mapToLong(Day34FileInfo::sizeBytes).sum();
        int chunkSize = Math.max(1, properties.chunkSize() - properties.chunkOverlap());
        int chunks = corpusChars == 0 ? 0 : (int) ((corpusChars + chunkSize - 1) / chunkSize);
        String status = available && !codeFiles.isEmpty() ? "ok" : "degraded";
        return new Day34HealthResponse(status, workspace.root().toString(), available,
                codeFiles.size(), documents, chunks, (int) corpusChars, mcpStatus(), modelStatus());
    }

    public List<Day34ToolInfo> tools() {
        Day34Connection connection = client.connect();
        return client.listTools(connection);
    }

    public List<Day34FileInfo> files(String prefix, Integer limit) {
        return workspace.list(prefix, limit);
    }

    public Day34FileContent read(Day34ReadRequest request) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            throw new IllegalArgumentException("Не указан путь к файлу");
        }
        return workspace.content(request.path(), request.startLine(), request.maxLines());
    }

    public List<Day34SearchMatch> search(Day34SearchRequest request) {
        if (request == null || request.pattern() == null || request.pattern().isBlank()) {
            throw new IllegalArgumentException("Не указан шаблон поиска");
        }
        return workspace.grep(request.pattern(), request.glob(), request.maxResults());
    }

    public Day34RunResponse run(Day34RunRequest request) {
        return agent.run(request == null ? new Day34RunRequest(null, null, null, null, null) : request);
    }

    private Day34McpStatus mcpStatus() {
        String endpoint = "http://localhost:" + properties.serverPort() + properties.path();
        try {
            Day34Connection connection = client.connect();
            int tools = client.listTools(connection).size();
            return new Day34McpStatus(true, endpoint, tools, "");
        } catch (Day34McpException ex) {
            return new Day34McpStatus(false, endpoint, 0, ex.getMessage());
        } catch (RuntimeException ex) {
            return new Day34McpStatus(false, endpoint, 0,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }

    private Day34ModelStatus modelStatus() {
        try {
            String version = localLlm.version();
            boolean installed = localLlm.models().stream()
                    .anyMatch(model -> model.name() != null
                            && model.name().equalsIgnoreCase(localProperties.model()));
            return new Day34ModelStatus(localProperties.endpoint(), localProperties.model(),
                    true, version, installed, "");
        } catch (Day26LlmException ex) {
            return new Day34ModelStatus(localProperties.endpoint(), localProperties.model(),
                    false, "", false, ex.getMessage());
        } catch (RuntimeException ex) {
            return new Day34ModelStatus(localProperties.endpoint(), localProperties.model(),
                    false, "", false, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
        }
    }
}
