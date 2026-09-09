package com.yunovan.aiadvent.day07;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.util.List;

public record Day07ChatResponse(
        String dialogId,
        String request,
        String content,
        String model,
        int messageCount,
        long elapsedMs,
        List<ConversationMessage> history,
        List<DialogMemory> memory) {
}