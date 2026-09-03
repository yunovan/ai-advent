package com.yunovan.aiadvent.day04;

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
@RequestMapping("/api/day4")
public class Day04TemperatureController {

    private final Day04TemperatureService temperatureService;

    public Day04TemperatureController(Day04TemperatureService temperatureService) {
        this.temperatureService = temperatureService;
    }

    @PostMapping("/compare")
    public TemperatureResponse compare(@RequestBody TemperatureRequest request) {
        return run(request == null ? null : request.prompt());
    }

    @GetMapping("/compare")
    public TemperatureResponse compare(@RequestParam String prompt) {
        return run(prompt);
    }

    private TemperatureResponse run(String prompt) {
        try {
            return temperatureService.compare(prompt);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
