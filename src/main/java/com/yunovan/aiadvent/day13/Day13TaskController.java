package com.yunovan.aiadvent.day13;

import com.yunovan.aiadvent.llm.LlmException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/day13")
public class Day13TaskController {

    private final Day13TaskService service;

    public Day13TaskController(Day13TaskService service) {
        this.service = service;
    }

    @PostMapping("/tasks")
    public Day13Task create(@RequestBody(required = false) Day13CreateTaskRequest request) {
        return invoke(() -> service.create(request == null ? null : request.title()));
    }

    @GetMapping("/tasks")
    public List<Day13Task> tasks() {
        return invoke(service::list);
    }

    @GetMapping("/tasks/{taskId}")
    public Day13Task get(@PathVariable String taskId) {
        return invoke(() -> service.get(taskId));
    }

    @GetMapping("/tasks/{taskId}/state")
    public Day13TaskState state(@PathVariable String taskId) {
        return invoke(() -> service.state(taskId));
    }

    @PostMapping("/tasks/{taskId}/advance")
    public Day13TaskState advance(@PathVariable String taskId) {
        return invoke(() -> service.advance(taskId));
    }

    @PostMapping("/tasks/{taskId}/step")
    public Day13TaskState step(@PathVariable String taskId, @RequestBody(required = false) Day13StepRequest request) {
        return invoke(() -> service.setStep(taskId, request == null ? 0 : request.step()));
    }

    @PostMapping("/tasks/{taskId}/expected-action")
    public Day13TaskState expectedAction(
            @PathVariable String taskId, @RequestBody(required = false) Day13ExpectedActionRequest request) {
        return invoke(() -> service.setExpectedAction(
                taskId, request == null ? null : request.expectedAction()));
    }

    @PostMapping("/tasks/{taskId}/note")
    public Day13TaskState note(@PathVariable String taskId, @RequestBody(required = false) Day13NoteRequest request) {
        return invoke(() -> service.addNote(taskId, request == null ? null : request.note()));
    }

    @PostMapping("/tasks/{taskId}/pause")
    public Day13TaskState pause(@PathVariable String taskId) {
        return invoke(() -> service.pause(taskId));
    }

    @PostMapping("/tasks/{taskId}/resume")
    public Day13TaskState resume(@PathVariable String taskId) {
        return invoke(() -> service.resume(taskId));
    }

    @PostMapping("/tasks/{taskId}/continue")
    public Day13ContinueResponse continueTask(
            @PathVariable String taskId, @RequestBody(required = false) Day13ContinueRequest request) {
        return invoke(() -> service.continueTask(
                taskId,
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit()));
    }

    private static <T> T invoke(Supplier<T> action) {
        try {
            return action.get();
        } catch (Day13TaskNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}