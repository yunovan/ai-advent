package com.yunovan.aiadvent.day17;

import java.util.List;

public interface Day17TrackerApi {

    Day17Ticket createTask(String title, String description, String assignee);

    List<Day17Ticket> listTasks(String status);

    Day17Comment addComment(String taskId, String text);
}