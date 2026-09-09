package com.yunovan.aiadvent.day08;

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
@RequestMapping("/api/day8")
public class Day08TokenController {

    private final Day08TokenService service;

    public Day08TokenController(Day08TokenService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    public Day08ChatResponse chat(@RequestBody Day08ChatRequest request) {
        return ask(
                request == null ? null : request.sessionId(),
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit());
    }

    @GetMapping("/chat")
    public Day08ChatResponse chat(
            @RequestParam String request,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) Long contextLimit) {
        return ask(sessionId, request, contextLimit);
    }

    @GetMapping("/metrics")
    public Day08GrowthReport metrics(@RequestParam(required = false, defaultValue = "default") String sessionId) {
        try {
            return service.metrics(sessionId);
        } catch (ConversationStoreException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    @PostMapping("/reset")
    public Day08ResetResponse reset(@RequestBody Day08ResetRequest request) {
        return reset(request == null ? null : request.sessionId());
    }

    @GetMapping("/reset")
    public Day08ResetResponse reset(@RequestParam(required = false) String sessionId) {
        try {
            service.reset(sessionId);
            return new Day08ResetResponse(ContextualChatAgent.normalize(sessionId), "История диалога удалена");
        } catch (ConversationStoreException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }

    private Day08ChatResponse ask(String sessionId, String userRequest, Long contextLimit) {
        try {
            return service.chat(sessionId, userRequest, contextLimit);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (ConversationStoreException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}