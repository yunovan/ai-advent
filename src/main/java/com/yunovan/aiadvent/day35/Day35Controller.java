package com.yunovan.aiadvent.day35;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping({ "/api/day35", "" })
public class Day35Controller {
    private final Day35MathTutorService service;
    public Day35Controller(Day35MathTutorService service){ this.service=service; }
    @GetMapping("/lessons") @ResponseBody public List<Day35Lesson> lessons(){ return service.lessons(); }
    @PostMapping("/sessions") @ResponseBody public Day35SessionResponse start(@RequestBody(required=false) Day35SessionRequest req){ return service.start(req==null?new Day35SessionRequest(null,null,null,null,null):req); }
    @PostMapping("/sessions/{id}/answer") @ResponseBody public Day35SessionResponse answer(@PathVariable String id, @RequestBody Day35SessionRequest req, Day35State state){ throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,"Demo mode"); }
}