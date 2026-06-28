package com.flashsale;

import com.flashsale.exception.IdempotencyConflictException;
import com.flashsale.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private IdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new IdempotencyService(redisTemplate);
    }

    @Test
    @DisplayName("Should treat first idempotency key as new request")
    void testFirstRequest() {
        when(valueOperations.setIfAbsent(eq("idempotency:KEY-1"), eq("hash1"), any(Duration.class)))
                .thenReturn(true);

        boolean replay = idempotencyService.isReplay("KEY-1", "hash1");

        assertThat(replay).isFalse();
    }

    @Test
    @DisplayName("Should detect replay when key and payload hash match")
    void testReplay() {
        when(valueOperations.setIfAbsent(eq("idempotency:KEY-2"), eq("hash2"), any(Duration.class)))
                .thenReturn(false);
        when(valueOperations.get("idempotency:KEY-2")).thenReturn("hash2");

        boolean replay = idempotencyService.isReplay("KEY-2", "hash2");

        assertThat(replay).isTrue();
    }

    @Test
    @DisplayName("Should reject same idempotency key with different payload")
    void testConflict() {
        when(valueOperations.setIfAbsent(eq("idempotency:KEY-3"), eq("new-hash"), any(Duration.class)))
                .thenReturn(false);
        when(valueOperations.get("idempotency:KEY-3")).thenReturn("old-hash");

        assertThatThrownBy(() -> idempotencyService.isReplay("KEY-3", "new-hash"))
                .isInstanceOf(IdempotencyConflictException.class);
    }
}
