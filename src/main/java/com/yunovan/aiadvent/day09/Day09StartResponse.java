package com.yunovan.aiadvent.day09;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.time.Instant;
import java.util.List;

public record Day09StartResponse(
        String dialogId,
        Instant createdAt,
        List<ConversationMessage> history,
        List<DialogMemory> memory) {
}