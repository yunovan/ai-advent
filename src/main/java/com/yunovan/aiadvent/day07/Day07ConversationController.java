package com.yunovan.aiadvent.day07;

import com.yunovan.aiadvent.agent.ConversationReply;
import com.yunovan.aiadvent.agent.ConversationalAgent;
import com.yunovan.aiadvent.agent.ContextualChatAgent;
import com.yunovan.aiadvent.agent.store.ConversationStoreException;
import com.yunovan.aiadvent.llm.LlmException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/day7")
public class Day07ConversationController {

    private final ConversationalAgent agent;

    public Day07ConversationController(ConversationalAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public ConversationResponse chat(@RequestBody ConversationRequest request) {
        return ask(request == null ? null : request.sessionId(), request == null ? null : request.request());
    }

    @GetMapping("/chat")
    public ConversationResponse chat(
            @RequestParam String request,
            @RequestParam(required = false, defaultValue = ContextualChatAgent.DEFAULT_SESSION_ID) String sessionId) {
        return ask(sessionId, request);
    }

    @PostMapping("/reset")
    public ResetResponse reset(@RequestBody SessionRequest request) {
        String sessionId = request == null ? null : request.sessionId();
        return reset(sessionId);
    }

    @GetMapping("/reset")
    public ResetResponse reset(@RequestParam(required = false) String sessionId) {
        try {
            String resolved = ContextualChatAgent.normalize(sessionId);
            agent.reset(resolved);
            return new ResetResponse(resolved, "История диалога удалена");
        } catch (ConversationStoreException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    private ConversationResponse ask(String sessionId, String userRequest) {
        try {
            ConversationReply reply = agent.ask(sessionId, userRequest);
            return new ConversationResponse(
                    reply.sessionId(),
                    userRequest,
                    reply.content(),
                    reply.model(),
                    reply.messageCount(),
                    reply.totalTokens(),
                    reply.costUsd(),
                    reply.elapsedMs(),
                    reply.messages());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (ConversationStoreException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}