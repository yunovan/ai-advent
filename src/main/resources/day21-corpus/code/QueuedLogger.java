package com.yunovan.samples;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Обучающий образец асинхронного логгера: сообщения складываются в очередь и
 * записываются фоновым потоком. Используется как пример структуры в корпусе кода.
 */
public final class QueuedLogger {

    private final Deque<String> queue = new ArrayDeque<>();
    private final Thread worker;
    private volatile boolean running = true;

    public QueuedLogger() {
        worker = new Thread(this::drain, "queued-logger");
        worker.setDaemon(true);
        worker.start();
    }

    public void info(String message) {
        queue.addLast("INFO " + message);
    }

    public void warn(String message) {
        queue.addLast("WARN " + message);
    }

    private void drain() {
        while (running) {
            try {
                String message = queue.pollFirst();
                if (message != null) {
                    System.out.println(message);
                    continue;
                }
                Thread.sleep(10L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void shutdown() {
        running = false;
    }

    public static void main(String[] args) throws InterruptedException {
        QueuedLogger logger = new QueuedLogger();
        logger.info("Индекс документов построен");
        logger.warn("Стратегия structural дала лучший коэффициент вариации");
        Thread.sleep(50L);
        logger.shutdown();
    }
}