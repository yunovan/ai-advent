package com.yunovan.aiadvent.day07;

import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.agent.dialog.DialogStoreException;
import com.yunovan.aiadvent.llm.LlmException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/day7")
public class Day07DialogController {

    private final Day07DialogService service;

    public Day07DialogController(Day07DialogService service) {
        this.service = service;
    }

    @PostMapping("/dialogs")
    public Day07StartResponse start() {
        return service.start();
    }

    @GetMapping("/dialogs")
    public List<Day07DialogSummary> dialogs() {
        return invoke(service::dialogs);
    }

    @GetMapping("/dialogs/{dialogId}")
    public Day07DialogInfo get(@PathVariable String dialogId) {
        return invoke(() -> service.get(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/chat")
    public Day07ChatResponse chat(
            @PathVariable String dialogId, @RequestBody Day07ChatRequest request) {
        return invoke(() -> service.chat(dialogId, request == null ? null : request.request()));
    }

    @GetMapping("/dialogs/{dialogId}/chat")
    public Day07ChatResponse chat(@PathVariable String dialogId, @RequestParam String request) {
        return invoke(() -> service.chat(dialogId, request));
    }

    @PostMapping("/dialogs/{dialogId}/finish")
    public Day07FinishResponse finish(@PathVariable String dialogId) {
        return invoke(() -> service.finish(dialogId));
    }

    @GetMapping("/dialogs/{dialogId}/finish")
    public Day07FinishResponse finishGet(@PathVariable String dialogId) {
        return invoke(() -> service.finish(dialogId));
    }

    private static <T> T invoke(Supplier<T> action) {
        try {
            return action.get();
        } catch (DialogNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (DialogStoreException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        }
    }
}