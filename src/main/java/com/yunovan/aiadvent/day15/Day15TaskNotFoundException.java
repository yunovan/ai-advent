package com.yunovan.aiadvent.day15;

public class Day15TaskNotFoundException extends RuntimeException {

    public Day15TaskNotFoundException(String taskId) {
        super("Задача '" + taskId + "' не найдена");
    }
}