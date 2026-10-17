package com.simpissuehub.application;

import java.time.Instant;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {
    private static final int MAX_FAILURES = 5;
    private static final long WINDOW_SECONDS = 15 * 60L;
    private final ConcurrentHashMap<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public boolean allowed(String key) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            discardExpired(attempts, now);
            return attempts.size() < MAX_FAILURES;
        }
    }

    public void failed(String key) {
        long now = Instant.now().getEpochSecond();
        Deque<Long> attempts = failures.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (attempts) {
            discardExpired(attempts, now);
            attempts.addLast(now);
        }
    }

    public void succeeded(String key) { failures.remove(key); }

    private void discardExpired(Deque<Long> attempts, long now) {
        while (!attempts.isEmpty() && now - attempts.peekFirst() >= WINDOW_SECONDS) attempts.removeFirst();
    }
}
