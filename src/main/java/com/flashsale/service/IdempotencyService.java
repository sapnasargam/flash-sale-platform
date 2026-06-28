package com.flashsale.service;

import com.flashsale.exception.IdempotencyConflictException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Stores a request payload hash per idempotency key.
 * Same key + same payload is a replay; same key + different payload is rejected.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration TTL = Duration.ofHours(24);

    public String generateHash(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate hash", e);
        }
    }

    public boolean isReplay(String idempotencyKey, String payloadHash) {
        String redisKey = KEY_PREFIX + idempotencyKey;

        Boolean stored = redisTemplate.opsForValue().setIfAbsent(redisKey, payloadHash, TTL);
        if (Boolean.TRUE.equals(stored)) {
            return false;
        }

        String storedHash = redisTemplate.opsForValue().get(redisKey);
        if (payloadHash.equals(storedHash)) {
            log.info("Idempotency replay detected for key: {}", idempotencyKey);
            return true;
        }

        log.warn("Idempotency conflict for key: {}", idempotencyKey);
        throw new IdempotencyConflictException();
    }
}
