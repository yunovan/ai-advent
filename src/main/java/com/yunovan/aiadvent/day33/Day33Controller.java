package com.yunovan.aiadvent.day33;

import com.yunovan.aiadvent.day26.Day26LlmException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day33")
public class Day33Controller {

    private final Day33SupportService service;

    public Day33Controller(Day33SupportService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day33HealthResponse health() {
        return service.health();
    }

    @GetMapping("/kb")
    @ResponseBody
    public List<Day33DocumentInfo> kb() {
        return service.kb();
    }

    @GetMapping("/tools")
    @ResponseBody
    public Day33ToolsResponse tools() {
        return service.tools();
    }

    @GetMapping("/users")
    @ResponseBody
    public List<Day33UserInfo> users() {
        return service.users();
    }

    @GetMapping("/tickets")
    @ResponseBody
    public List<Day33TicketInfo> tickets() {
        return service.tickets();
    }

    @PostMapping("/crm")
    @ResponseBody
    public Day33CrmResponse crm(@RequestBody(required = false) Day33CrmRequest request) {
        return invoke(() -> service.crm(
                request == null ? null : request.tool(),
                request == null ? null : request.arguments()));
    }

    @PostMapping("/ask")
    @ResponseBody
    public Day33SupportResponse ask(@RequestBody(required = false) Day33SupportRequest request) {
        return invoke(() -> service.ask(
                request == null ? null : request.question(),
                request == null ? null : request.ticketId(),
                request == null ? null : request.userId()));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Day33SupportException | Day33McpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (Day26LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
