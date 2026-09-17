package com.yunovan.aiadvent.day14;

import com.yunovan.aiadvent.llm.LlmException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/day14")
public class Day14InvariantController {

    private final Day14InvariantService service;

    public Day14InvariantController(Day14InvariantService service) {
        this.service = service;
    }

    @PostMapping("/invariants")
    public Day14Invariant create(@RequestBody(required = false) Day14CreateInvariantRequest request) {
        return invoke(() -> service.create(
                request == null ? null : request.category(),
                request == null ? null : request.title(),
                request == null ? null : request.description()));
    }

    @GetMapping("/invariants")
    public List<Day14Invariant> invariants() {
        return invoke(service::list);
    }

    @GetMapping("/invariants/active")
    public List<Day14Invariant> active() {
        return invoke(service::active);
    }

    @GetMapping("/invariants/{invariantId}")
    public Day14Invariant get(@PathVariable String invariantId) {
        return invoke(() -> service.get(invariantId));
    }

    @PostMapping("/invariants/{invariantId}/deactivate")
    public Day14Invariant deactivate(@PathVariable String invariantId) {
        return invoke(() -> service.deactivate(invariantId));
    }

    @DeleteMapping("/invariants/{invariantId}")
    public void delete(@PathVariable String invariantId) {
        invoke(() -> service.delete(invariantId));
    }

    @PostMapping("/advise")
    public Day14AdviseResponse advise(@RequestBody(required = false) Day14AdviseRequest request) {
        return invoke(() -> service.advise(
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit()));
    }

    private static <T> T invoke(Supplier<T> action) {
        try {
            return action.get();
        } catch (Day14InvariantNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}