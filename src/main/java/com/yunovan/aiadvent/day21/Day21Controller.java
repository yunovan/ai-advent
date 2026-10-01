package com.yunovan.aiadvent.day21;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day21")
public class Day21Controller {

    private final Day21IndexFacade facade;
    private final Day21AgentService service;

    public Day21Controller(Day21IndexFacade facade, Day21AgentService service) {
        this.facade = facade;
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day21HealthResponse health() {
        return invoke(facade::health);
    }

    @GetMapping("/strategies")
    @ResponseBody
    public List<Day21StrategyInfo> strategies() {
        return invoke(() -> List.of(
                facade.strategyInfo(Day21Properties.STRATEGY_FIXED),
                facade.strategyInfo(Day21Properties.STRATEGY_STRUCTURAL)));
    }

    @PostMapping("/ingest")
    @ResponseBody
    public Day21IngestResponse ingest(@RequestBody(required = false) Day21IngestRequest request) {
        return invoke(() -> facade.ingest(request == null ? null : request.strategy()));
    }

    @PostMapping("/search")
    @ResponseBody
    public Day21SearchResponse search(@RequestBody(required = false) Day21SearchRequest request) {
        return invoke(() -> facade.search(
                request == null ? null : request.strategy(),
                request == null ? "" : request.query(),
                request == null ? null : request.k()));
    }

    @GetMapping("/compare")
    @ResponseBody
    public Day21ComparisonResponse compare() {
        return invoke(facade::compare);
    }

    @GetMapping("/chunks")
    @ResponseBody
    public List<Day21Chunk> chunks(@RequestParam(name = "strategy", required = false) String strategy) {
        return invoke(() -> facade.chunks(strategy));
    }

    @PostMapping("/agent")
    @ResponseBody
    public Day21AgentResponse agent(@RequestBody(required = false) Day21AgentRequest request) {
        return invoke(() -> service.submit(request == null ? "" : request.prompt()));
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