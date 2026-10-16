package com.yunovan.aiadvent.day35;

import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class Day35MathTutorService {
    private final Day35Properties props;
    private final Day35ProblemGenerator gen;
    private final Day35LessonRepository repo;
    private final Day35Tutor tutor;
    private final Day26LocalLlmClient llm;

    public Day35MathTutorService(Day35Properties props, Day35ProblemGenerator gen, Day35LessonRepository repo, Day35Tutor tutor, Day26LocalLlmClient llm) {
        this.props = props; this.gen = gen; this.repo = repo; this.tutor = tutor; this.llm = llm;
    }

    public List<Day35Lesson> lessons() { return repo.all(); }

    public Day35SessionResponse start(Day35SessionRequest req) {
        long start = System.nanoTime();
        String topic = req.topic()==null||req.topic().isBlank()?"algebra":req.topic();
        String level = req.level()==null||req.level().isBlank()?"5-7":req.level();
        int count = req.count()==null||req.count()<1?props.maxProblems():Math.min(req.count(), props.maxProblems());
        List<Day35Problem> problems = new ArrayList<>();
        for(int i=0;i<count;i++) problems.add(gen.generate(topic,level));
        String sid = UUID.randomUUID().toString();
        Day35Problem cur = problems.getFirst();
        String task = "Тема: " + topic + ". Уровень: " + level + ". Задача " + 1 + " из " + count;
        Day35Progress p = new Day35Progress(count,0,0, List.of(topic));
        Day35Solution sol = new Day35Solution(cur.id(), "", "", "Подумайте и дайте ответ.", "");
        Day35Answer ans = null;
        long latency = (System.nanoTime()-start)/1_000_000L;
        return new Day35SessionResponse(sid,task,cur,sol,null,p,false,"Сессия начата. AI участвует через локальную модель при генерации/объяснении.","",latency,0,0,0.0);
    }

    public Day35SessionResponse answer(Day35SessionRequest req, String sid, Day35State state) {
        long start = System.nanoTime();
        Day35Problem cur = state.problems().stream().filter(x->x.id().equals(state.currentProblemId())).findFirst().orElse(state.problems().get(state.step()));
        Day35Solution sol = tutor.explain(cur, req.userAnswer());
        Day35ErrorAnalysis anal = tutor.analyze(cur, req.userAnswer());
        Day35Progress prog = new Day35Progress(state.problems().size(), state.step()+1, (sol.check()!=null&&sol.check().equals("correct"))?1:0, List.of(req.topic()==null?"algebra":req.topic()));
        boolean finished = (state.step()+1)>=state.problems().size();
        String task = "Шаг " + (state.step()+1) + " из " + state.problems().size();
        String note = finished?"Обучение завершено. Проверьте прогресс.":"Продолжайте решать задачи с пошаговым разбором.";
        long latency = (System.nanoTime()-start)/1_000_000L;
        return new Day35SessionResponse(sid,task,cur,sol,anal,prog,finished,note,"",latency,0,0,0.0);
    }
}