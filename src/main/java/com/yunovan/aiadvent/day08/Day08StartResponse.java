package com.yunovan.aiadvent.day08;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.time.Instant;
import java.util.List;

public record Day08StartResponse(
        String dialogId,
        Instant createdAt,
        List<ConversationMessage> history,
        List<DialogMemory> memory) {
}