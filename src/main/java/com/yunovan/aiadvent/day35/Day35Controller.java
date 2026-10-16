package com.yunovan.aiadvent.day35;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("${day35.path:/api/day35}")
public class Day35Controller {

    private final Day35MathTutorService service;
    private final Day35HealthService healthService;

    public Day35Controller(Day35MathTutorService service, Day35HealthService healthService) {
        this.service = service;
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day35HealthService.Day35Health health() {
        return healthService.health();
    }

    @GetMapping("/lessons")
    @ResponseBody
    public List<Day35Lesson> lessons() {
        return service.lessons();
    }

    @PostMapping("/sessions")
    @ResponseBody
    public Day35SessionResponse start(@RequestBody(required = false) Day35SessionRequest request) {
        return service.start(request);
    }

    @PostMapping("/sessions/{id}/answer")
    @ResponseBody
    public Day35SessionResponse answer(@PathVariable String id,
                                       @RequestBody(required = false) Day35SessionRequest request) {
        try {
            return service.answer(id, request);
        } catch (Day35Exception ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }
    }
}
