package com.yunovan.aiadvent.day09;

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
@RequestMapping("/api/day9")
public class Day09DialogController {

    private final Day09DialogService service;

    public Day09DialogController(Day09DialogService service) {
        this.service = service;
    }

    @PostMapping("/dialogs")
    public Day09StartResponse start() {
        return service.start();
    }

    @GetMapping("/dialogs")
    public List<Day09DialogSummary> dialogs() {
        return invoke(service::dialogs);
    }

    @GetMapping("/dialogs/{dialogId}")
    public Day09DialogInfo get(@PathVariable String dialogId) {
        return invoke(() -> service.get(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/chat")
    public Day09ChatResponse chat(@PathVariable String dialogId, @RequestBody Day09ChatRequest request) {
        return invoke(() -> service.chat(
                dialogId,
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit(),
                request == null ? null : request.compression()));
    }

    @GetMapping("/dialogs/{dialogId}/chat")
    public Day09ChatResponse chatGet(
            @PathVariable String dialogId,
            @RequestParam String request,
            @RequestParam(required = false) Long contextLimit,
            @RequestParam(required = false) Boolean compression) {
        return invoke(() -> service.chat(dialogId, request, contextLimit, compression));
    }

    @GetMapping("/dialogs/{dialogId}/metrics")
    public Day09GrowthReport metrics(@PathVariable String dialogId) {
        return invoke(() -> service.metrics(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/finish")
    public Day09FinishResponse finish(@PathVariable String dialogId) {
        return invoke(() -> service.finish(dialogId));
    }

    @GetMapping("/dialogs/{dialogId}/finish")
    public Day09FinishResponse finishGet(@PathVariable String dialogId) {
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