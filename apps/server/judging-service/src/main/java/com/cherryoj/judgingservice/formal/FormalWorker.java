package com.cherryoj.judgingservice.formal;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cherry.formal.enabled", havingValue = "true")
public class FormalWorker {
    private final FormalTaskStore store;
    private final FormalAttemptRunner attempts;
    private final FormalProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ConcurrentMap<String, FormalTaskStore.Task> active = new ConcurrentHashMap<>();
    private final Semaphore slots;

    FormalWorker(FormalTaskStore store, FormalAttemptRunner attempts, FormalProperties properties) {
        this.store = store;
        this.attempts = attempts;
        this.properties = properties;
        slots = new Semaphore(properties.parallelism());
    }

    @Scheduled(fixedDelay = 500, scheduler = "formalTaskScheduler")
    public void poll() {
        for (var task : active.values()) store.renew(task, properties.leaseDuration());
        while (slots.tryAcquire()) {
            FormalTaskStore.Task task;
            try {
                task = store.claim(properties.leaseDuration());
            } catch (RuntimeException error) {
                slots.release();
                throw error;
            }
            if (task == null) {
                slots.release();
                return;
            }
            active.put(task.id(), task);
            try {
                executor.submit(() -> {
                    try {
                        attempts.execute(task);
                    } finally {
                        active.remove(task.id(), task);
                        slots.release();
                    }
                });
            } catch (RejectedExecutionException stopping) {
                active.remove(task.id(), task);
                slots.release();
                return;
            }
        }
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}
