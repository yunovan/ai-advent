package com.yunovan.aiadvent.day24;

import com.yunovan.aiadvent.day21.Day21IndexException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day24")
public class Day24Controller {

    private final Day24Service service;

    public Day24Controller(Day24Service service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day24HealthResponse health() {
        return invoke(service::health);
    }

    @GetMapping("/questions")
    @ResponseBody
    public List<Day24ControlQuestion> questions() {
        return invoke(service::questions);
    }

    @PostMapping("/answer")
    @ResponseBody
    public Day24GroundedResponse answer(@RequestBody(required = false) Day24AnswerRequest request) {
        return invoke(() -> service.answer(request == null ? null : request.question()));
    }

    @PostMapping("/evaluate")
    @ResponseBody
    public Day24EvalResponse evaluate() {
        return invoke(service::evaluate);
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Day21IndexException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}