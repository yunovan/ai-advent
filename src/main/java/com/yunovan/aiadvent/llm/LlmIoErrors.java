package com.yunovan.aiadvent.llm;

import org.springframework.web.client.RestClientResponseException;

final class LlmIoErrors {

    private LlmIoErrors() {
    }

    static boolean isRetryable(Throwable error) {
        return !(error instanceof RestClientResponseException);
    }

    static String describe(Throwable error) {
        if (error == null) {
            return "unknown error";
        }
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (!text.isEmpty()) {
                text.append(" <- ");
            }
            text.append(current.getClass().getSimpleName());
            String message = current.getMessage();
            if (message != null && !message.isBlank() && !"null".equalsIgnoreCase(message.trim())) {
                text.append(": ").append(message);
            }
            current = current.getCause();
        }
        return text.toString();
    }
}
