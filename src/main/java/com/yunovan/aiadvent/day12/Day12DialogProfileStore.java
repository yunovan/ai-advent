package com.yunovan.aiadvent.day12;

import com.yunovan.aiadvent.agent.dialog.DialogStoreException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

public class Day12DialogProfileStore {

    private final JsonMapper objectMapper;
    private final Path root;

    public Day12DialogProfileStore(Path root) {
        this.objectMapper = JsonMapper.builder().build();
        this.root = root;
    }

    public synchronized void assign(String dialogId, String profileId) {
        try {
            Files.createDirectories(root);
            ObjectNode node = objectMapper.createObjectNode();
            node.put("profileId", profileId);
            objectMapper.writeValue(fileFor(dialogId).toFile(), node);
        } catch (Exception ex) {
            throw new DialogStoreException(
                    "Failed to link profile to dialog '" + dialogId + "': " + ex.getMessage(), ex);
        }
    }

    public synchronized String profileIdFor(String dialogId) {
        Path file = fileFor(dialogId);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(file.toFile());
            JsonNode profileId = node.get("profileId");
            return profileId == null || profileId.isNull() ? null : profileId.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private Path fileFor(String dialogId) {
        return root.resolve(dialogId + ".json");
    }
}