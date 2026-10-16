package com.yunovan.aiadvent.day35;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day35CliRunner implements ApplicationRunner {
    private final ApplicationContext context;
    public Day35CliRunner(ApplicationContext context){ this.context=context; }
    @Override public void run(ApplicationArguments args){
        if(!"35".equals(first(args,"day"))) return;
        if(args.containsOption("check")){
            System.out.println("День 35: AI-тьютор по математике");
            System.out.println("Реальная задача: пошаговое обучение решению задач с разбором ошибок");
            System.out.println("AI участвует: генерация задач, объяснение, анализ ошибок через локальную LLM");
            maybeExit(args); return;
        }
        System.out.println("День 35: http://localhost:8080/day35.html | CLI: --day=35 --check");
        maybeExit(args);
    }
    private String first(ApplicationArguments a,String n){ var v=a.getOptionValues(n); return v==null||v.isEmpty()?null:v.getFirst(); }
    private void maybeExit(ApplicationArguments a){ if(a.containsOption("cli")){ SpringApplication.exit(context,()->0); System.exit(0); } }
}