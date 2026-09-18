package com.yunovan.aiadvent.day15;

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
@RequestMapping("/api/day15")
public class Day15TaskController {

    private final Day15TaskService service;

    public Day15TaskController(Day15TaskService service) {
        this.service = service;
    }

    @PostMapping("/tasks")
    public Day15Task create(@RequestBody(required = false) Day15CreateTaskRequest request) {
        return invoke(() -> service.create(request == null ? null : request.title()));
    }

    @GetMapping("/tasks")
    public List<Day15Task> tasks() {
        return invoke(service::list);
    }

    @GetMapping("/tasks/{taskId}")
    public Day15Task get(@PathVariable String taskId) {
        return invoke(() -> service.get(taskId));
    }

    @GetMapping("/tasks/{taskId}/state")
    public Day15TaskState state(@PathVariable String taskId) {
        return invoke(() -> service.state(taskId));
    }

    @PostMapping("/tasks/{taskId}/transition")
    public Day15TaskState transition(
            @PathVariable String taskId, @RequestBody(required = false) Day15TransitionRequest request) {
        return invoke(() -> service.transition(
                taskId, request == null ? null : request.target()));
    }

    @PostMapping("/tasks/{taskId}/advance")
    public Day15TaskState advance(@PathVariable String taskId) {
        return invoke(() -> service.advance(taskId));
    }

    @PostMapping("/tasks/{taskId}/step")
    public Day15TaskState step(@PathVariable String taskId, @RequestBody(required = false) Day15StepRequest request) {
        return invoke(() -> service.setStep(taskId, request == null ? 0 : request.step()));
    }

    @PostMapping("/tasks/{taskId}/expected-action")
    public Day15TaskState expectedAction(
            @PathVariable String taskId, @RequestBody(required = false) Day15ExpectedActionRequest request) {
        return invoke(() -> service.setExpectedAction(
                taskId, request == null ? null : request.expectedAction()));
    }

    @PostMapping("/tasks/{taskId}/note")
    public Day15TaskState note(@PathVariable String taskId, @RequestBody(required = false) Day15NoteRequest request) {
        return invoke(() -> service.addNote(taskId, request == null ? null : request.note()));
    }

    @PostMapping("/tasks/{taskId}/pause")
    public Day15TaskState pause(@PathVariable String taskId) {
        return invoke(() -> service.pause(taskId));
    }

    @PostMapping("/tasks/{taskId}/resume")
    public Day15TaskState resume(@PathVariable String taskId) {
        return invoke(() -> service.resume(taskId));
    }

    @PostMapping("/tasks/{taskId}/continue")
    public Day15ContinueResponse continueTask(
            @PathVariable String taskId, @RequestBody(required = false) Day15ContinueRequest request) {
        return invoke(() -> service.continueTask(
                taskId,
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit()));
    }

    private static <T> T invoke(Supplier<T> action) {
        try {
            return action.get();
        } catch (Day15TaskNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}