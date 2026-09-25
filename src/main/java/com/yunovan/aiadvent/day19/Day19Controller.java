package com.yunovan.aiadvent.day19;

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
@RequestMapping("/api/day19")
public class Day19Controller {

    private final Day19AgentService service;
    private final Day19MarketApi market;
    private final Day19SaveService saveService;

    public Day19Controller(Day19AgentService service, Day19MarketApi market, Day19SaveService saveService) {
        this.service = service;
        this.market = market;
        this.saveService = saveService;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day19HealthResponse health() {
        return invoke(service::health);
    }

    @GetMapping("/tools")
    @ResponseBody
    public List<Day19ToolInfo> tools() {
        return invoke(service::tools);
    }

    @PostMapping("/search")
    @ResponseBody
    public List<Day19Product> search(@RequestBody(required = false) Day19SearchRequest request) {
        return invoke(() -> market.search(request == null ? null : request.query(),
                request == null ? null : request.category(),
                request == null ? null : request.maxResults(),
                request == null ? null : request.sort()));
    }

    @PostMapping("/summarize")
    @ResponseBody
    public String summarize(@RequestBody(required = false) Day19SummarizeRequest request) {
        return invoke(() -> market.summarizeQuery(
                request == null ? "" : request.query(),
                request == null ? null : request.format()));
    }

    @PostMapping("/save")
    @ResponseBody
    public Day19SavedFile save(@RequestBody(required = false) Day19SaveRequest request) {
        return invoke(() -> market.saveQuery(
                request == null ? "" : request.query(),
                request == null ? null : request.format(),
                request == null ? null : request.fileName()));
    }

    @PostMapping("/pipeline")
    @ResponseBody
    public Day19PipelineResponse pipeline(@RequestBody(required = false) Day19PipelineRequest request) {
        return invoke(() -> service.pipeline(
                request == null ? "" : request.query(),
                request == null ? null : request.format(),
                request == null ? null : request.fileName()));
    }

    @PostMapping("/agent")
    @ResponseBody
    public Day19AgentResponse agent(@RequestBody(required = false) Day19AgentRequest request) {
        return invoke(() -> service.submit(request == null ? "" : request.prompt()));
    }

    @GetMapping("/files")
    @ResponseBody
    public List<Day19SavedFile> files(@RequestParam(name = "refresh", required = false) Boolean refresh) {
        return invoke(saveService::list);
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Day19McpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}