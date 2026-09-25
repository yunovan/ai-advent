package com.yunovan.aiadvent.day19;

import java.util.List;

public interface Day19MarketApi {

    List<Day19Product> search(String query, String category, Integer maxResults, String sort);

    String summarize(String query, String data, String format);

    String summarizeQuery(String query, String format);

    Day19SavedFile saveFile(String data, String summary, String format, String fileName);

    Day19SavedFile saveQuery(String query, String format, String fileName);
}