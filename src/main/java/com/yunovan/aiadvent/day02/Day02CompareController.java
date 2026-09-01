package com.yunovan.aiadvent.day02;

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
@RequestMapping("/api/day2")
public class Day02CompareController {

    private final Day02CompareService compareService;

    public Day02CompareController(Day02CompareService compareService) {
        this.compareService = compareService;
    }

    @PostMapping("/compare")
    public CompareResponse compare(@RequestBody CompareRequest request) {
        return run(request == null ? null : request.prompt());
    }

    @GetMapping("/compare")
    public CompareResponse compare(@RequestParam String prompt) {
        return run(prompt);
    }

    private CompareResponse run(String prompt) {
        try {
            return compareService.compare(prompt);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
