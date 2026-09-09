package com.yunovan.aiadvent.agent.dialog;

import com.yunovan.aiadvent.day07.Day7Properties;
import com.yunovan.aiadvent.day08.Day8Properties;
import java.nio.file.Path;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DialogStoreConfig {

    private final Day7Properties day7;
    private final Day8Properties day8;

    public DialogStoreConfig(Day7Properties day7, Day8Properties day8) {
        this.day7 = day7;
        this.day8 = day8;
    }

    @Bean
    public DialogStore day7DialogStore() {
        return new FileDialogStore(Path.of(day7.dialogDir()));
    }

    @Bean
    public DialogStore day8DialogStore() {
        return new FileDialogStore(Path.of(day8.dialogDir()));
    }
}