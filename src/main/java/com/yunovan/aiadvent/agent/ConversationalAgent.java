package com.yunovan.aiadvent.agent;

public interface ConversationalAgent {

    ConversationReply ask(String sessionId, String userRequest);

    void reset(String sessionId);
}