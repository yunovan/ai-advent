package com.yunovan.aiadvent.agent.store;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.agent.Conversation;
import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.day07.Day7Properties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class FileConversationStoreTest {

    @TempDir
    Path tempDir;

    private FileConversationStore store() {
        return new FileConversationStore(JsonMapper.builder().build(), new Day7Properties(tempDir.toString(), 40));
    }

    @Test
    void loadReturnsEmptyConversationForUnknownSession() {
        Conversation conversation = store().load("unknown");

        assertThat(conversation.sessionId()).isEqualTo("unknown");
        assertThat(conversation.messages()).isEmpty();
    }

    @Test
    void saveThenLoadRoundTripsMessages() {
        FileConversationStore store = store();
        Conversation saved = new Conversation(
                        "default",
                        java.time.Instant.parse("2026-09-08T10:00:00Z"),
                        List.of(ConversationMessage.system("sys"), ConversationMessage.user("hi")));

        store.save(saved);
        Conversation loaded = store.load("default");

        assertThat(loaded.sessionId()).isEqualTo("default");
        assertThat(loaded.createdAt()).isEqualTo(java.time.Instant.parse("2026-09-08T10:00:00Z"));
        assertThat(loaded.messages()).containsExactly(
                ConversationMessage.system("sys"), ConversationMessage.user("hi"));
    }

    @Test
    void saveWritesJsonFileNamedAfterSession() throws Exception {
        store().save(Conversation.empty("alice"));

        assertThat(Files.exists(tempDir.resolve("alice.json"))).isTrue();
        assertThat(Files.exists(tempDir.resolve("default.json"))).isFalse();
    }

    @Test
    void deleteRemovesStoredConversation() {
        FileConversationStore store = store();
        store.save(Conversation.empty("default"));

        store.delete("default");

        assertThat(store.load("default").messages()).isEmpty();
        assertThat(Files.exists(tempDir.resolve("default.json"))).isFalse();
    }
}