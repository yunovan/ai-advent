package com.yunovan.aiadvent.day20;

import java.util.List;
import java.util.Map;

public final class Day20Registry {

    public static final String SCHEDULER_SERVER = "scheduler";
    public static final String MARKET_SERVER = "market";

    private static final Map<String, String> TOOLS = Map.ofEntries(
            Map.entry("scheduler_add_reminder", SCHEDULER_SERVER),
            Map.entry("scheduler_add_collector", SCHEDULER_SERVER),
            Map.entry("scheduler_list_jobs", SCHEDULER_SERVER),
            Map.entry("scheduler_summary", SCHEDULER_SERVER),
            Map.entry("scheduler_run_now", SCHEDULER_SERVER),
            Map.entry("scheduler_stop_process", SCHEDULER_SERVER),
            Map.entry("search", MARKET_SERVER),
            Map.entry("summarize", MARKET_SERVER),
            Map.entry("saveToFile", MARKET_SERVER));

    private Day20Registry() {
    }

    public static String serverFor(String tool) {
        String server = TOOLS.get(tool);
        if (server == null) {
            throw new IllegalArgumentException("Инструмент «" + tool + "» не зарегистрирован ни на одном "
                    + "MCP-сервере. Зарегистрированные инструменты: " + String.join(", ", TOOLS.keySet()));
        }
        return server;
    }

    public static List<String> allServerIds() {
        return List.of(SCHEDULER_SERVER, MARKET_SERVER);
    }
}