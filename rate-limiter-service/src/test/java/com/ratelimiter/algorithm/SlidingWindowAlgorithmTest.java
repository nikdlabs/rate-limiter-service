package com.ratelimiter.algorithm;

import com.ratelimiter.model.RuleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowAlgorithmTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    private RuleConfig rule;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rule = new RuleConfig("OTP", 5, 60, "sliding_window");
    }

    private SlidingWindowAlgorithm algorithmAt(long epochSeconds) {
        Clock fixedClock = Clock.fixed(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
        return new SlidingWindowAlgorithm(redisTemplate, fixedClock);
    }

    @Test
    @DisplayName("Start of a fresh window, no previous data — allowed, TTL set to 2x window")
    void freshWindow_noPrevious_allowedAndDoubleTTLSet() {
        when(valueOps.increment("rl:user123:OTP:2")).thenReturn(1L);
        when(valueOps.get("rl:user123:OTP:1")).thenReturn(null);

        boolean result = algorithmAt(120).isAllowed("user123", "OTP", rule);

        assertTrue(result);
        verify(redisTemplate).expire("rl:user123:OTP:2", 120L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("Mid-window, moderate previous traffic — blended count still under limit, allowed")
    void midWindow_moderatePrevious_allowed() {
        when(valueOps.increment("rl:user123:OTP:2")).thenReturn(2L);
        when(valueOps.get("rl:user123:OTP:1")).thenReturn("4");

        boolean result = algorithmAt(150).isAllowed("user123", "OTP", rule);

        assertTrue(result);
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("THE FIX: request right after boundary, heavy previous traffic — correctly denied")
    void justAfterBoundary_heavyPrevious_deniedByWeightedCount() {
        when(valueOps.increment("rl:user123:OTP:2")).thenReturn(1L);
        when(valueOps.get("rl:user123:OTP:1")).thenReturn("5");

        boolean result = algorithmAt(121).isAllowed("user123", "OTP", rule);

        assertFalse(result);
    }

    @Test
    @DisplayName("getRemaining — blends both windows, floors conservatively")
    void getRemaining_blendsBothWindows() {
        when(valueOps.get("rl:user123:OTP:2")).thenReturn("2");
        when(valueOps.get("rl:user123:OTP:1")).thenReturn("4");

        int remaining = algorithmAt(150).getRemaining("user123", "OTP", rule);

        assertEquals(1, remaining);
    }

    @Test
    @DisplayName("getRemaining — no data in either window, returns full limit")
    void getRemaining_noData_returnsFullLimit() {
        when(valueOps.get("rl:user123:OTP:2")).thenReturn(null);
        when(valueOps.get("rl:user123:OTP:1")).thenReturn(null);

        int remaining = algorithmAt(150).getRemaining("user123", "OTP", rule);

        assertEquals(5, remaining);
    }

    @Test
    @DisplayName("getResetAfterSeconds — mid-window, pure clock math, no Redis call")
    void getResetAfterSeconds_midWindow_noRedisCall() {
        long resetAfter = algorithmAt(150).getResetAfterSeconds("user123", "OTP", rule);

        assertEquals(30L, resetAfter);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("getResetAfterSeconds — exact start of window, returns full window length")
    void getResetAfterSeconds_windowStart_returnsFullWindow() {
        long resetAfter = algorithmAt(120).getResetAfterSeconds("user123", "OTP", rule);

        assertEquals(60L, resetAfter);
    }

    @Test
    @DisplayName("reset — deletes both current and previous bucket keys")
    void reset_deletesCurrentAndPreviousBucketKeys() {
        algorithmAt(150).reset("user123", "OTP", rule);

        verify(redisTemplate).delete(List.of("rl:user123:OTP:2", "rl:user123:OTP:1"));
    }
}