package com.yunovan.aiadvent.day11;

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
@RequestMapping("/api/day11")
public class Day11DialogController {

    private final Day11DialogService service;

    public Day11DialogController(Day11DialogService service) {
        this.service = service;
    }

    @PostMapping("/dialogs")
    public Day11StartResponse start() {
        return invoke(service::start);
    }

    @GetMapping("/dialogs")
    public List<Day11DialogInfo> dialogs() {
        return invoke(() -> service.dialogs().stream()
                .map(dialog -> service.get(dialog.id()))
                .toList());
    }

    @GetMapping("/dialogs/{dialogId}")
    public Day11DialogInfo get(@PathVariable String dialogId) {
        return invoke(() -> service.get(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/chat")
    public Day11ChatResponse chat(
            @PathVariable String dialogId, @RequestBody(required = false) Day11ChatRequest request) {
        return invoke(() -> service.chat(
                dialogId,
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit()));
    }

    @GetMapping("/dialogs/{dialogId}/chat")
    public Day11ChatResponse chatGet(
            @PathVariable String dialogId,
            @RequestParam String request,
            @RequestParam(required = false) Long contextLimit) {
        return invoke(() -> service.chat(dialogId, request, contextLimit));
    }

    @PostMapping("/dialogs/{dialogId}/remember")
    public Day11DialogInfo remember(
            @PathVariable String dialogId, @RequestBody(required = false) Day11RememberRequest request) {
        return invoke(() -> service.remember(
                dialogId,
                request == null ? null : request.key(),
                request == null ? null : request.value(),
                request == null ? null : request.layer()));
    }

    @PostMapping("/dialogs/{dialogId}/promote")
    public Day11DialogInfo promote(
            @PathVariable String dialogId, @RequestBody(required = false) Day11PromoteRequest request) {
        return invoke(() -> service.promote(
                dialogId,
                request == null ? null : request.key(),
                request == null ? null : request.toLayer()));
    }

    @PostMapping("/dialogs/{dialogId}/decide")
    public Day11DialogInfo decide(
            @PathVariable String dialogId, @RequestBody(required = false) Day11DecideRequest request) {
        return invoke(() -> service.decide(
                dialogId,
                request == null ? null : request.statement()));
    }

    @PostMapping("/dialogs/{dialogId}/forget")
    public Day11DialogInfo forget(
            @PathVariable String dialogId,
            @RequestParam String layer,
            @RequestParam String key) {
        return invoke(() -> service.forget(dialogId, layer, key));
    }

    @GetMapping("/dialogs/{dialogId}/metrics")
    public Day11MetricsReport metrics(@PathVariable String dialogId) {
        return invoke(() -> service.metrics(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/finish")
    public Day11FinishResponse finish(@PathVariable String dialogId) {
        return invoke(() -> service.finish(dialogId));
    }

    @GetMapping("/dialogs/{dialogId}/finish")
    public Day11FinishResponse finishGet(@PathVariable String dialogId) {
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