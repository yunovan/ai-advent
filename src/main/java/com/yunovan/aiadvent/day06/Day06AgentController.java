package com.yunovan.aiadvent.day06;

import com.yunovan.aiadvent.agent.Agent;
import com.yunovan.aiadvent.agent.AgentReply;
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
@RequestMapping("/api/day6")
public class Day06AgentController {

    private final Agent agent;

    public Day06AgentController(Agent agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody AgentRequest request) {
        return run(request == null ? null : request.request());
    }

    @GetMapping("/chat")
    public AgentResponse chat(@RequestParam String request) {
        return run(request);
    }

    private AgentResponse run(String userRequest) {
        try {
            AgentReply reply = agent.ask(userRequest);
            return new AgentResponse(userRequest, reply.content(), reply.model(), reply.totalTokens(), reply.costUsd(), reply.elapsedMs());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}