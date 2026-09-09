package com.yunovan.aiadvent.agent.dialog;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DialogContext {

    public static final String BASE_PROMPT =
            "Ты — AI-агент, часть учебного проекта AI Advent. У тебя есть долговременная память: "
                    + "краткие итоги прошлых завершённых диалогов. Пользуйся ими, когда это уместно. "
                    + "Отвечай кратко и по делу.";

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(ZoneId.systemDefault());

    public String systemPrompt(List<Dialog> finishedDialogs) {
        StringBuilder builder = new StringBuilder(BASE_PROMPT);
        builder.append("\n\nПрошлые завершённые диалоги (от новых к старым):");
        if (finishedDialogs == null || finishedDialogs.isEmpty()) {
            builder.append("\nпока нет.");
        } else {
            for (Dialog dialog : finishedDialogs) {
                builder.append("\n- ").append(label(dialog));
            }
        }
        return builder.toString();
    }

    private static String label(Dialog dialog) {
        String when = dialog.finishedAt() == null ? "?" : TIME.format(dialog.finishedAt());
        String summary = dialog.summary() == null || dialog.summary().isBlank()
                ? "без итога"
                : dialog.summary().trim();
        return "диалог от " + when + " (" + dialog.messages().size() + " сообщений): " + summary;
    }
}