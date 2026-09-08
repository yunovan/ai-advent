package com.yunovan.aiadvent.agent.store;

import com.yunovan.aiadvent.agent.Conversation;

public interface ConversationStore {

    Conversation load(String sessionId);

    void save(Conversation conversation);

    void delete(String sessionId);
}