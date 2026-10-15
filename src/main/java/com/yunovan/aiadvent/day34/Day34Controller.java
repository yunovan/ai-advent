package com.yunovan.aiadvent.day34;

import com.yunovan.aiadvent.day26.Day26LlmException;
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
@RequestMapping("/api/day34")
public class Day34Controller {

    private final Day34FileService service;

    public Day34Controller(Day34FileService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day34HealthResponse health() {
        return service.health();
    }

    @GetMapping("/tools")
    @ResponseBody
    public List<Day34ToolInfo> tools() {
        return invoke(service::tools);
    }

    @GetMapping("/files")
    @ResponseBody
    public List<Day34FileInfo> files(@RequestParam(required = false) String prefix,
                                     @RequestParam(required = false) Integer limit) {
        return invoke(() -> service.files(prefix, limit));
    }

    @PostMapping("/read")
    @ResponseBody
    public Day34FileContent read(@RequestBody(required = false) Day34ReadRequest request) {
        return invoke(() -> service.read(request));
    }

    @PostMapping("/search")
    @ResponseBody
    public List<Day34SearchMatch> search(@RequestBody(required = false) Day34SearchRequest request) {
        return invoke(() -> service.search(request));
    }

    @PostMapping("/run")
    @ResponseBody
    public Day34RunResponse run(@RequestBody(required = false) Day34RunRequest request) {
        return invoke(() -> service.run(request));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Day34McpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (Day34Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Day26LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
