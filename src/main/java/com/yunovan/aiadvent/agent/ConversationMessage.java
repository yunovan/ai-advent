package com.yunovan.aiadvent.agent;

public record ConversationMessage(String role, String content) {

    public static ConversationMessage user(String content) {
        return new ConversationMessage("user", content);
    }

    public static ConversationMessage assistant(String content) {
        return new ConversationMessage("assistant", content);
    }

    public static ConversationMessage system(String content) {
        return new ConversationMessage("system", content);
    }
}