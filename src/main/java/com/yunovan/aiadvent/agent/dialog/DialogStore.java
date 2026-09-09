package com.yunovan.aiadvent.agent.dialog;

import java.util.List;

public interface DialogStore {

    Dialog create();

    Dialog load(String id);

    void save(Dialog dialog);

    List<Dialog> finishedDialogs();
}