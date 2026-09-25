package com.yunovan.aiadvent.day18;

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
@RequestMapping("/api/day18")
public class Day18Controller {

    private final Day18AgentService service;
    private final Day18SchedulerApi scheduler;

    public Day18Controller(Day18AgentService service, Day18SchedulerApi scheduler) {
        this.service = service;
        this.scheduler = scheduler;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day18HealthResponse health() {
        return invoke(service::health);
    }

    @GetMapping("/tools")
    @ResponseBody
    public List<Day18ToolInfo> tools() {
        return invoke(service::tools);
    }

    @PostMapping("/agent")
    @ResponseBody
    public Day18AgentResponse agent(@RequestBody(required = false) Day18AgentRequest request) {
        return invoke(() -> service.submit(request == null ? "" : request.prompt()));
    }

    @GetMapping("/jobs")
    @ResponseBody
    public List<Day18Job> jobs() {
        return invoke(scheduler::listJobs);
    }

    @GetMapping("/summary")
    @ResponseBody
    public Day18Summary summary(@RequestParam(name = "feed", required = false) String feed,
                                @RequestParam(name = "sinceSeconds", required = false) Integer sinceSeconds) {
        return invoke(() -> scheduler.summary(feed, sinceSeconds));
    }

    @GetMapping("/samples")
    @ResponseBody
    public List<Day18Sample> samples() {
        return invoke(() -> scheduler.samplesFor(null, null));
    }

    @PostMapping("/reminder")
    @ResponseBody
    public Day18Job reminder(@RequestBody(required = false) Day18ReminderRequest request) {
        return invoke(() -> scheduler.addReminder(request == null ? null : request.topic(),
                request == null ? null : request.delaySeconds()));
    }

    @PostMapping("/collector")
    @ResponseBody
    public Day18Job collector(@RequestBody(required = false) Day18CollectorRequest request) {
        return invoke(() -> scheduler.addCollector(request == null ? null : request.feed(),
                request == null ? null : request.periodSeconds(),
                request == null ? null : request.url(),
                request == null ? null : request.sourceFeed()));
    }

    @PostMapping("/run")
    @ResponseBody
    public Day18Job run(@RequestBody(required = false) Day18RunRequest request) {
        return invoke(() -> scheduler.runNow(request == null ? null : request.jobId()));
    }

    @PostMapping("/stop")
    @ResponseBody
    public List<Day18Job> stop(@RequestBody(required = false) Day18RunRequest request) {
        return invoke(() -> scheduler.stopProcess(request == null ? null : request.jobId()));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Day18McpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}