package com.yunovan.aiadvent;

import com.yunovan.aiadvent.day05.Day5Properties;
import com.yunovan.aiadvent.day07.Day7Properties;
import com.yunovan.aiadvent.day08.Day8Properties;
import com.yunovan.aiadvent.day09.Day9Properties;
import com.yunovan.aiadvent.day10.Day10Properties;
import com.yunovan.aiadvent.day11.Day11Properties;
import com.yunovan.aiadvent.day12.Day12Properties;
import com.yunovan.aiadvent.day13.Day13Properties;
import com.yunovan.aiadvent.day14.Day14Properties;
import com.yunovan.aiadvent.day15.Day15Properties;
import com.yunovan.aiadvent.day16.Day16Properties;
import com.yunovan.aiadvent.day17.Day17Properties;
import com.yunovan.aiadvent.day18.Day18Properties;
import com.yunovan.aiadvent.day19.Day19Properties;
import com.yunovan.aiadvent.day20.Day20Properties;
import com.yunovan.aiadvent.llm.LlmHttpProperties;
import com.yunovan.aiadvent.llm.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    LlmProperties.class,
    LlmHttpProperties.class,
    Day5Properties.class,
    Day7Properties.class,
    Day8Properties.class,
    Day9Properties.class,
    Day10Properties.class,
    Day11Properties.class,
    Day12Properties.class,
    Day13Properties.class,
    Day14Properties.class,
    Day15Properties.class,
    Day16Properties.class,
    Day17Properties.class,
    Day18Properties.class,
    Day19Properties.class,
    Day20Properties.class
})
public class AiAdventApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAdventApplication.class, args);
    }
}
