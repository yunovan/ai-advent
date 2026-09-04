package com.yunovan.aiadvent.day05;

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
@RequestMapping("/api/day5")
public class Day05CompareController {

    private final Day05CompareService compareService;

    public Day05CompareController(Day05CompareService compareService) {
        this.compareService = compareService;
    }

    @PostMapping("/compare")
    public ModelComparisonResponse compare(@RequestBody ModelComparisonRequest request) {
        return run(request == null ? null : request.prompt());
    }

    @GetMapping("/compare")
    public ModelComparisonResponse compare(@RequestParam String prompt) {
        return run(prompt);
    }

    private ModelComparisonResponse run(String prompt) {
        try {
            return compareService.compare(prompt);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
