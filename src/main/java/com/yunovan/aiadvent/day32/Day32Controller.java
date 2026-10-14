package com.yunovan.aiadvent.day32;

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
@RequestMapping("/api/day32")
public class Day32Controller {

    private final Day32ReviewService service;

    public Day32Controller(Day32ReviewService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day32HealthResponse health() {
        return service.health();
    }

    @GetMapping("/changed")
    @ResponseBody
    public List<Day32ChangedFile> changed(@RequestParam(required = false) String base,
                                          @RequestParam(required = false) String head) {
        return invoke(() -> service.changedFiles(base, head));
    }

    @PostMapping("/review")
    @ResponseBody
    public Day32ReviewResponse review(@RequestBody(required = false) Day32ReviewRequest request) {
        return invoke(() -> service.review(request));
    }

    @PostMapping("/review-current")
    @ResponseBody
    public Day32ReviewResponse reviewCurrent(@RequestBody(required = false) Day32ReviewRequest request) {
        Day32ReviewRequest effective = new Day32ReviewRequest(
                request == null ? null : request.base(),
                null,
                null,
                request == null ? null : request.title(),
                request == null ? null : request.description());
        return invoke(() -> service.review(effective));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Day32GitException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (Day26LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
