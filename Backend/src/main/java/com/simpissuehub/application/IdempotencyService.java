package com.simpissuehub.application;

import com.simpissuehub.domain.IdempotencyRecord;
import com.simpissuehub.domain.User;
import com.simpissuehub.repository.IdempotencyRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository records;
    public IdempotencyService(IdempotencyRecordRepository records) { this.records = records; }

    public String normalizeKey(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > 128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key 不能超过 128 个字符");
        return normalized;
    }

    public String hash(String... values) {
        try {
            String source = String.join("\n", values);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append("%02x".formatted(value));
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    public IdempotencyRecord find(User user, String operation, String key, String requestHash) {
        if (key == null) return null;
        IdempotencyRecord existing = records.findByUserIdAndOperationAndIdempotencyKey(user.id, operation, key).orElse(null);
        if (existing != null && !existing.requestHash.equals(requestHash)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key 已用于其他请求");
        return existing;
    }

    public IdempotencyRecord save(User user, String operation, String key, String requestHash, String resourceType, String resourceId) {
        if (key == null) return null;
        IdempotencyRecord record = new IdempotencyRecord(); record.user = user; record.operation = operation; record.idempotencyKey = key; record.requestHash = requestHash; record.resourceType = resourceType; record.resourceId = resourceId;
        return records.saveAndFlush(record);
    }
}
