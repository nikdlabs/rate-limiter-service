package com.ratelimiter.algorithm;

import com.ratelimiter.model.RuleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBucketAlgorithmTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOps;

    @InjectMocks
    private TokenBucketAlgorithm algorithm;

    private RuleConfig rule;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOps);
        rule = new RuleConfig("OTP", 5, 60, "token_bucket");
    }

    @Test
    @DisplayName("New user — bucket starts full, request allowed")
    void newUser_bucketStartsFull_allowed() {
        when(hashOps.get(anyString(), eq("tokens"))).thenReturn(null);
        when(hashOps.get(anyString(), eq("lastRefill"))).thenReturn(null);

        boolean result = algorithm.isAllowed("user123", "OTP", rule);

        assertTrue(result);
        verify(hashOps).put(anyString(), eq("tokens"), eq("4.0"));
    }

    @Test
    @DisplayName("Bucket has tokens, no time elapsed — allowed, one token consumed")
    void bucketHasTokens_noElapsedTime_allowedAndConsumesOne() {
        long now = System.currentTimeMillis() / 1000L;
        when(hashOps.get(anyString(), eq("tokens"))).thenReturn("3.0");
        when(hashOps.get(anyString(), eq("lastRefill"))).thenReturn(String.valueOf(now));

        boolean result = algorithm.isAllowed("user123", "OTP", rule);

        assertTrue(result);
        verify(hashOps).put(anyString(), eq("tokens"), eq("2.0"));
    }

    @Test
    @DisplayName("Bucket empty, no time elapsed — request denied")
    void bucketEmpty_noElapsedTime_denied() {
        long now = System.currentTimeMillis() / 1000L;
        when(hashOps.get(anyString(), eq("tokens"))).thenReturn("0.0");
        when(hashOps.get(anyString(), eq("lastRefill"))).thenReturn(String.valueOf(now));

        boolean result = algorithm.isAllowed("user123", "OTP", rule);

        assertFalse(result);
        verify(hashOps).put(anyString(), eq("tokens"), eq("0.0"));
    }

    @Test
    @DisplayName("Bucket refills over elapsed time, capped at limit")
    void bucketRefills_cappedAtLimit() {
        long lastRefill = (System.currentTimeMillis() / 1000L) - 120;
        when(hashOps.get(anyString(), eq("tokens"))).thenReturn("0.0");
        when(hashOps.get(anyString(), eq("lastRefill"))).thenReturn(String.valueOf(lastRefill));

        int remaining = algorithm.getRemaining("user123", "OTP", rule);

        assertEquals(5, remaining);
    }

    @Test
    @DisplayName("getRemaining — floors partial tokens to whole number")
    void getRemaining_floorsPartialTokens() {
        long now = System.currentTimeMillis() / 1000L;
        when(hashOps.get(anyString(), eq("tokens"))).thenReturn("2.9");
        when(hashOps.get(anyString(), eq("lastRefill"))).thenReturn(String.valueOf(now));

        int remaining = algorithm.getRemaining("user123", "OTP", rule);

        assertEquals(2, remaining);
    }

    @Test
    @DisplayName("getResetAfterSeconds — bucket already full returns 0")
    void getResetAfterSeconds_bucketFull_returnsZero() {
        long now = System.currentTimeMillis() / 1000L;
        when(hashOps.get(anyString(), eq("tokens"))).thenReturn("5.0");
        when(hashOps.get(anyString(), eq("lastRefill"))).thenReturn(String.valueOf(now));

        long resetAfter = algorithm.getResetAfterSeconds("user123", "OTP", rule);

        assertEquals(0L, resetAfter);
    }

    @Test
    @DisplayName("getResetAfterSeconds — empty bucket returns full window time to refill")
    void getResetAfterSeconds_emptyBucket_returnsWindowSeconds() {
        long now = System.currentTimeMillis() / 1000L;
        when(hashOps.get(anyString(), eq("tokens"))).thenReturn("0.0");
        when(hashOps.get(anyString(), eq("lastRefill"))).thenReturn(String.valueOf(now));

        long resetAfter = algorithm.getResetAfterSeconds("user123", "OTP", rule);

        assertEquals(60L, resetAfter);
    }

    @Test
    @DisplayName("reset — deletes the token bucket hash key")
    void reset_deletesHashKey() {
        algorithm.reset("user123", "OTP", rule);

        verify(redisTemplate).delete("tb:user123:OTP");
    }
}