package com.yunovan.aiadvent.day13;

public class Day13TaskNotFoundException extends RuntimeException {

    public Day13TaskNotFoundException(String taskId) {
        super("Задача '" + taskId + "' не найдена");
    }
}