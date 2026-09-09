package com.yunovan.aiadvent.agent.dialog;

public class DialogNotFoundException extends RuntimeException {

    public DialogNotFoundException(String dialogId) {
        super("Диалог '" + dialogId + "' не найден");
    }
}