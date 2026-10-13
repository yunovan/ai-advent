package com.yunovan.aiadvent.day31;

public record Day31GitInfo(
        boolean available,
        String branch,
        String commit,
        boolean dirty,
        String reason) {

    public static Day31GitInfo unavailable(String reason) {
        return new Day31GitInfo(false, "", "", false, reason == null ? "" : reason);
    }
}
