package com.yunovan.aiadvent.day12;

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
@RequestMapping("/api/day12")
public class Day12DialogController {

    private final Day12DialogService service;

    public Day12DialogController(Day12DialogService service) {
        this.service = service;
    }

    @PostMapping("/dialogs")
    public Day12StartResponse start(@RequestParam(required = false) String profile) {
        return invoke(() -> service.start(profile));
    }

    @GetMapping("/dialogs")
    public List<Day12DialogInfo> dialogs() {
        return invoke(service::dialogs);
    }

    @GetMapping("/dialogs/{dialogId}")
    public Day12DialogInfo get(@PathVariable String dialogId) {
        return invoke(() -> service.get(dialogId));
    }

    @PostMapping("/dialogs/{dialogId}/chat")
    public Day12ChatResponse chat(
            @PathVariable String dialogId, @RequestBody(required = false) Day12ChatRequest request) {
        return invoke(() -> service.chat(
                dialogId,
                request == null ? null : request.request(),
                request == null ? null : request.contextLimit()));
    }

    @PostMapping("/dialogs/{dialogId}/profile")
    public Day12DialogInfo setProfile(
            @PathVariable String dialogId, @RequestBody(required = false) Day12SetProfileRequest request) {
        return invoke(() -> service.setProfile(
                dialogId,
                request == null ? null : request.profileId()));
    }

    @PostMapping("/dialogs/{dialogId}/remember")
    public Day12DialogInfo remember(
            @PathVariable String dialogId, @RequestBody(required = false) Day12RememberRequest request) {
        return invoke(() -> service.remember(
                dialogId,
                request == null ? null : request.key(),
                request == null ? null : request.value(),
                request == null ? null : request.layer()));
    }

    @PostMapping("/dialogs/{dialogId}/finish")
    public Day12FinishResponse finish(@PathVariable String dialogId) {
        return invoke(() -> service.finish(dialogId));
    }

    @GetMapping("/profiles")
    public List<Day12Profile> profiles() {
        return invoke(service::profiles);
    }

    @PostMapping("/profiles")
    public Day12Profile createProfile(@RequestBody(required = false) Day12CreateProfileRequest request) {
        return invoke(() -> service.createProfile(
                request == null ? null : request.name(),
                request == null ? null : request.style(),
                request == null ? null : request.format(),
                request == null ? null : request.restrictions(),
                request == null ? null : request.notes()));
    }

    @GetMapping("/profiles/{id}")
    public Day12Profile profile(@PathVariable String id) {
        Day12Profile profile = invoke(() -> service.profile(id));
        if (profile == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль '" + id + "' не найден");
        }
        return profile;
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