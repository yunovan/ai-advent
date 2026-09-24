package com.yunovan.aiadvent.day17;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day17/tracker")
public class Day17MockApiController {

    private final Day17TrackerApi api;

    public Day17MockApiController(Day17TrackerApi api) {
        this.api = api;
    }

    @GetMapping("/tasks")
    @ResponseBody
    public List<Day17Ticket> tasks(@RequestParam(name = "status", required = false) String status) {
        return api.listTasks(status);
    }

    @PostMapping("/tasks")
    @ResponseBody
    public Day17Ticket create(@RequestBody(required = false) Day17CreateTaskRequest request) {
        return guard(() -> api.createTask(
                request == null ? null : request.title(),
                request == null ? null : request.description(),
                request == null ? null : request.assignee()));
    }

    @PostMapping("/tasks/{id}/comments")
    @ResponseBody
    public Day17Comment addComment(@PathVariable("id") String id,
                                   @RequestBody(required = false) Day17AddCommentRequest request) {
        return guard(() -> api.addComment(id, request == null ? null : request.text()));
    }

    private static <T> T guard(java.util.function.Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}