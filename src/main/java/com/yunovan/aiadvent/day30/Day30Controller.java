package com.yunovan.aiadvent.day30;

import com.yunovan.aiadvent.day26.Day26LlmException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day30")
public class Day30Controller {

    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final String CLIENT_ID_HEADER = "X-Client-Id";

    private final Day30PrivateLlmService service;

    public Day30Controller(Day30PrivateLlmService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day30HealthResponse health() {
        return service.health();
    }

    @PostMapping("/chat")
    @ResponseBody
    public Day30ChatResponse chat(
            @RequestBody(required = false) Day30ChatRequest request,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            HttpServletRequest http) {
        return invoke(() -> service.chat(request, clientKey(http), apiKey));
    }

    @PostMapping("/stress")
    @ResponseBody
    public Day30StressResponse stress(
            @RequestBody(required = false) Day30StressRequest request,
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            HttpServletRequest http) {
        return invoke(() -> service.stress(request, clientKey(http), apiKey));
    }

    private static String clientKey(HttpServletRequest http) {
        String header = http.getHeader(CLIENT_ID_HEADER);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        return http.getRemoteAddr() == null ? "unknown" : http.getRemoteAddr();
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Day30AuthException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage(), ex);
        } catch (Day30RateLimitException ex) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), ex);
        } catch (Day26LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
