package com.yunovan.aiadvent.day03;

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
@RequestMapping("/api/day3")
public class Day03CompareController {

    private final Day03ReasoningService reasoningService;

    public Day03CompareController(Day03ReasoningService reasoningService) {
        this.reasoningService = reasoningService;
    }

    @PostMapping("/compare")
    public ReasoningResponse compare(@RequestBody ReasoningRequest request) {
        return run(request == null ? null : request.prompt());
    }

    @GetMapping("/compare")
    public ReasoningResponse compare(@RequestParam String prompt) {
        return run(prompt);
    }

    private ReasoningResponse run(String prompt) {
        try {
            return reasoningService.solve(prompt);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
