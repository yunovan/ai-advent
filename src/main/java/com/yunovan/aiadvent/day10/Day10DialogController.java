package com.yunovan.aiadvent.day10;

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
@RequestMapping("/api/day10")
public class Day10DialogController {

    private final Day10DialogService service;

    public Day10DialogController(Day10DialogService service) {
        this.service = service;
    }

    @PostMapping("/dialogs")
    public Day10StartResponse start(
            @RequestParam(required = false) String strategy,
            @RequestParam(required = false) Integer windowSize) {
        return invoke(() -> service.start(strategy, windowSize));
    }

    @GetMapping("/dialogs")
    public List<Day10DialogSummary> dialogs() {
        return invoke(service::dialogs);
    }

    @GetMapping("/dialogs/{dialogId}")
    public Day10DialogInfo get(@PathVariable String dialogId) {
        return invoke(() -> service.get(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/chat")
    public Day10ChatResponse chat(@PathVariable String dialogId, @RequestBody(required = false) Day10ChatRequest request) {
        return invoke(() -> service.chat(
                dialogId,
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit(),
                request == null ? null : request.windowSize()));
    }

    @GetMapping("/dialogs/{dialogId}/chat")
    public Day10ChatResponse chatGet(
            @PathVariable String dialogId,
            @RequestParam String request,
            @RequestParam(required = false) Long contextLimit,
            @RequestParam(required = false) Integer windowSize) {
        return invoke(() -> service.chat(dialogId, request, contextLimit, windowSize));
    }

    @PostMapping("/dialogs/{dialogId}/facts")
    public Day10DialogInfo addFact(@PathVariable String dialogId, @RequestBody Day10FactRequest request) {
        return invoke(() -> service.addFact(
                dialogId,
                request == null ? null : request.key(),
                request == null ? null : request.value(),
                request == null ? null : request.active()));
    }

    @PostMapping("/dialogs/{dialogId}/checkpoint")
    public Day10DialogInfo checkpoint(@PathVariable String dialogId) {
        return invoke(() -> service.checkpoint(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/branches")
    public Day10DialogInfo createBranch(@PathVariable String dialogId) {
        return invoke(() -> service.createBranch(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/branches/{branchId}/activate")
    public Day10DialogInfo switchBranch(@PathVariable String dialogId, @PathVariable String branchId) {
        return invoke(() -> service.switchBranch(dialogId, branchId));
    }

    @GetMapping("/dialogs/{dialogId}/metrics")
    public Day10GrowthReport metrics(@PathVariable String dialogId) {
        return invoke(() -> service.metrics(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/finish")
    public Day10FinishResponse finish(@PathVariable String dialogId) {
        return invoke(() -> service.finish(dialogId));
    }

    @GetMapping("/dialogs/{dialogId}/finish")
    public Day10FinishResponse finishGet(@PathVariable String dialogId) {
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