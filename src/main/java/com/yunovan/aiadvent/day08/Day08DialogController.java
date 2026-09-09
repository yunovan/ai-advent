package com.yunovan.aiadvent.day08;

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
@RequestMapping("/api/day8")
public class Day08DialogController {

    private final Day08DialogService service;

    public Day08DialogController(Day08DialogService service) {
        this.service = service;
    }

    @PostMapping("/dialogs")
    public Day08StartResponse start() {
        return service.start();
    }

    @GetMapping("/dialogs")
    public List<Day08DialogSummary> dialogs() {
        return invoke(service::dialogs);
    }

    @GetMapping("/dialogs/{dialogId}")
    public Day08DialogInfo get(@PathVariable String dialogId) {
        return invoke(() -> service.get(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/chat")
    public Day08ChatResponse chat(
            @PathVariable String dialogId, @RequestBody Day08ChatRequest request) {
        return invoke(() -> service.chat(
                dialogId,
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit()));
    }

    @GetMapping("/dialogs/{dialogId}/chat")
    public Day08ChatResponse chat(
            @PathVariable String dialogId,
            @RequestParam String request,
            @RequestParam(required = false) Long contextLimit) {
        return invoke(() -> service.chat(dialogId, request, contextLimit));
    }

    @GetMapping("/dialogs/{dialogId}/metrics")
    public Day08GrowthReport metrics(@PathVariable String dialogId) {
        return invoke(() -> service.metrics(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/finish")
    public Day08FinishResponse finish(@PathVariable String dialogId) {
        return invoke(() -> service.finish(dialogId));
    }

    @GetMapping("/dialogs/{dialogId}/finish")
    public Day08FinishResponse finishGet(@PathVariable String dialogId) {
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