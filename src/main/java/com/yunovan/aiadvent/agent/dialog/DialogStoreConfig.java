package com.yunovan.aiadvent.agent.dialog;

import com.yunovan.aiadvent.day07.Day7Properties;
import com.yunovan.aiadvent.day08.Day8Properties;
import com.yunovan.aiadvent.day09.Day9Properties;
import com.yunovan.aiadvent.day10.Day10FileDialogStore;
import com.yunovan.aiadvent.day10.Day10Properties;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DialogStoreConfig {

    private final Day7Properties day7;
    private final Day8Properties day8;
    private final Day9Properties day9;
    private final Day10Properties day10;

    public DialogStoreConfig(Day7Properties day7, Day8Properties day8, Day9Properties day9, Day10Properties day10) {
        this.day7 = day7;
        this.day8 = day8;
        this.day9 = day9;
        this.day10 = day10;
    }

    @Bean
    public DialogStore day7DialogStore() {
        return new FileDialogStore(Path.of(day7.dialogDir()));
    }

    @Bean
    public DialogStore day8DialogStore() {
        return new FileDialogStore(Path.of(day8.dialogDir()));
    }

    @Bean
    public DialogStore day9DialogStore() {
        return new FileDialogStore(Path.of(day9.dialogDir()));
    }

    @Bean
    public Day10FileDialogStore day10DialogStore() {
        return new Day10FileDialogStore(Path.of(day10.dialogDir()));
    }
}