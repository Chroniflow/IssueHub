package com.simpissuehub.application;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks authentication requests per client IP and activates Turnstile after five per hour. */
@Component
public class TurnstileChallengeLimiter {
    private static final int THRESHOLD = 5;
    private static final long WINDOW_SECONDS = 3600;
    private final ConcurrentHashMap<String, Deque<Long>> requests = new ConcurrentHashMap<>();

    public boolean required(String ip) {
        Deque<Long> entries = requests.get(normalize(ip));
        if (entries == null) return false;
        synchronized (entries) {
            discardExpired(entries, Instant.now().getEpochSecond());
            return entries.size() >= THRESHOLD;
        }
    }

    public boolean recordAndCheck(String ip) {
        Deque<Long> entries = requests.computeIfAbsent(normalize(ip), ignored -> new ArrayDeque<>());
        long now = Instant.now().getEpochSecond();
        synchronized (entries) {
            discardExpired(entries, now);
            entries.addLast(now);
            return entries.size() > THRESHOLD;
        }
    }

    private String normalize(String ip) { return ip == null || ip.isBlank() ? "unknown" : ip.trim(); }
    private void discardExpired(Deque<Long> entries, long now) {
        while (!entries.isEmpty() && now - entries.peekFirst() >= WINDOW_SECONDS) entries.removeFirst();
    }
}
