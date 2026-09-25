package com.yunovan.aiadvent.day18;

public class Day18Job {

    private String id;
    private String type;
    private String name;
    private String feed;
    private String status;
    private Integer delaySeconds;
    private Integer periodSeconds;
    private String url;
    private String sourceFeed;
    private String createdAt;
    private String fireAt;
    private String nextRunAt;
    private Integer runCount;
    private String lastResult;

    public Day18Job() {
    }

    public Day18Job(String id, String type, String name, String feed, String status,
                    Integer delaySeconds, Integer periodSeconds, String url, String sourceFeed,
                    String createdAt, String fireAt, String nextRunAt) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.feed = feed;
        this.status = status;
        this.delaySeconds = delaySeconds;
        this.periodSeconds = periodSeconds;
        this.url = url;
        this.sourceFeed = sourceFeed;
        this.createdAt = createdAt;
        this.fireAt = fireAt;
        this.nextRunAt = nextRunAt;
        this.runCount = 0;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getFeed() {
        return feed;
    }

    public void setFeed(String feed) {
        this.feed = feed;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getDelaySeconds() {
        return delaySeconds;
    }

    public void setDelaySeconds(Integer delaySeconds) {
        this.delaySeconds = delaySeconds;
    }

    public Integer getPeriodSeconds() {
        return periodSeconds;
    }

    public void setPeriodSeconds(Integer periodSeconds) {
        this.periodSeconds = periodSeconds;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSourceFeed() {
        return sourceFeed;
    }

    public void setSourceFeed(String sourceFeed) {
        this.sourceFeed = sourceFeed;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFireAt() {
        return fireAt;
    }

    public void setFireAt(String fireAt) {
        this.fireAt = fireAt;
    }

    public String getNextRunAt() {
        return nextRunAt;
    }

    public void setNextRunAt(String nextRunAt) {
        this.nextRunAt = nextRunAt;
    }

    public Integer getRunCount() {
        return runCount;
    }

    public void setRunCount(Integer runCount) {
        this.runCount = runCount;
    }

    public String getLastResult() {
        return lastResult;
    }

    public void setLastResult(String lastResult) {
        this.lastResult = lastResult;
    }
}