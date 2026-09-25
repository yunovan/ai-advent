package com.yunovan.aiadvent.day18;

import java.util.List;

public interface Day18SchedulerApi {

    Day18Job addReminder(String topic, Integer delaySeconds);

    Day18Job addCollector(String feed, Integer periodSeconds, String url, String sourceFeed);

    Day18Job runNow(String jobId);

    List<Day18Job> stopProcess(String jobId);

    List<Day18Job> listJobs();

    Day18Summary summary(String feed, Integer sinceSeconds);

    List<Day18Sample> samplesFor(String feed, Integer sinceSeconds);
}