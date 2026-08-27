package com.ratelimiter.service;

import com.ratelimiter.algorithm.RateLimitAlgorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RuleConfig;
import com.ratelimiter.model.UsageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RuleService ruleService;

    @Mock
    private RateLimitAlgorithm slidingWindowMock;

    @Mock
    private RateLimitAlgorithm tokenBucketMock;

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        Map<String, RateLimitAlgorithm> algorithms = new HashMap<>();
        algorithms.put("sliding_window", slidingWindowMock);
        algorithms.put("token_bucket", tokenBucketMock);

        rateLimiterService = new RateLimiterService(ruleService, algorithms);
    }

    @Test
    @DisplayName("check — routes to Sliding Window when rule specifies it")
    void check_routesToSlidingWindow() {
        RuleConfig rule = new RuleConfig("OTP", 5, 60, "sliding_window");
        when(ruleService.getRule("OTP")).thenReturn(rule);
        when(slidingWindowMock.isAllowed("user123", "OTP", rule)).thenReturn(true);
        when(slidingWindowMock.getRemaining("user123", "OTP", rule)).thenReturn(4);
        when(slidingWindowMock.getResetAfterSeconds("user123", "OTP", rule)).thenReturn(42L);

        RateLimitResult result = rateLimiterService.check("user123", "OTP");

        assertTrue(result.isAllowed());
        assertEquals(4, result.getRemaining());
        assertEquals(42L, result.getResetAfterSeconds());
        assertEquals("sliding_window", result.getAlgorithm());
        verify(tokenBucketMock, never()).isAllowed(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("check — routes to Token Bucket when rule specifies it")
    void check_routesToTokenBucket() {
        RuleConfig rule = new RuleConfig("LOGIN", 3, 300, "token_bucket");
        when(ruleService.getRule("LOGIN")).thenReturn(rule);
        when(tokenBucketMock.isAllowed("user456", "LOGIN", rule)).thenReturn(true);
        when(tokenBucketMock.getRemaining("user456", "LOGIN", rule)).thenReturn(2);
        when(tokenBucketMock.getResetAfterSeconds("user456", "LOGIN", rule)).thenReturn(100L);

        RateLimitResult result = rateLimiterService.check("user456", "LOGIN");

        assertTrue(result.isAllowed());
        assertEquals(2, result.getRemaining());
        assertEquals("token_bucket", result.getAlgorithm());
        verify(slidingWindowMock, never()).isAllowed(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("check — denied request is passed through correctly")
    void check_deniedRequest_returnsAllowedFalse() {
        RuleConfig rule = new RuleConfig("OTP", 5, 60, "sliding_window");
        when(ruleService.getRule("OTP")).thenReturn(rule);
        when(slidingWindowMock.isAllowed("user123", "OTP", rule)).thenReturn(false);
        when(slidingWindowMock.getRemaining("user123", "OTP", rule)).thenReturn(0);
        when(slidingWindowMock.getResetAfterSeconds("user123", "OTP", rule)).thenReturn(15L);

        RateLimitResult result = rateLimiterService.check("user123", "OTP");

        assertFalse(result.isAllowed());
        assertEquals(0, result.getRemaining());
        assertEquals(15L, result.getResetAfterSeconds());
    }

    @Test
    @DisplayName("check — unknown algorithm in rule throws IllegalArgumentException")
    void check_unknownAlgorithm_throwsException() {
        RuleConfig rule = new RuleConfig("WEIRD", 5, 60, "leaky_bucket");
        when(ruleService.getRule("WEIRD")).thenReturn(rule);

        assertThrows(IllegalArgumentException.class,
                () -> rateLimiterService.check("user123", "WEIRD"));
    }

    @Test
    @DisplayName("check — calls RuleService.getRule exactly once per check")
    void check_callsGetRuleExactlyOnce() {
        RuleConfig rule = new RuleConfig("OTP", 5, 60, "sliding_window");
        when(ruleService.getRule("OTP")).thenReturn(rule);
        when(slidingWindowMock.isAllowed(anyString(), anyString(), any())).thenReturn(true);

        rateLimiterService.check("user123", "OTP");

        verify(ruleService, times(1)).getRule("OTP");
    }

    @Test
    @DisplayName("getUsage — reports remaining and reset time across all configured rules")
    void getUsage_reportsAcrossAllRules() {
        RuleConfig otpRule = new RuleConfig("OTP", 5, 60, "sliding_window");
        RuleConfig loginRule = new RuleConfig("LOGIN", 3, 300, "token_bucket");
        when(ruleService.getAllRules()).thenReturn(List.of(otpRule, loginRule));

        when(slidingWindowMock.getRemaining("user123", "OTP", otpRule)).thenReturn(4);
        when(slidingWindowMock.getResetAfterSeconds("user123", "OTP", otpRule)).thenReturn(42L);
        when(tokenBucketMock.getRemaining("user123", "LOGIN", loginRule)).thenReturn(2);
        when(tokenBucketMock.getResetAfterSeconds("user123", "LOGIN", loginRule)).thenReturn(100L);

        List<UsageInfo> usage = rateLimiterService.getUsage("user123");

        assertEquals(2, usage.size());
        assertEquals("OTP", usage.get(0).getAction());
        assertEquals(4, usage.get(0).getRemaining());
    }

    @Test
    @DisplayName("resetUser — calls reset on the correct algorithm for every configured rule")
    void resetUser_resetsEveryConfiguredRule() {
        RuleConfig otpRule = new RuleConfig("OTP", 5, 60, "sliding_window");
        RuleConfig loginRule = new RuleConfig("LOGIN", 3, 300, "token_bucket");
        when(ruleService.getAllRules()).thenReturn(List.of(otpRule, loginRule));

        rateLimiterService.resetUser("user123");

        verify(slidingWindowMock).reset("user123", "OTP", otpRule);
        verify(tokenBucketMock).reset("user123", "LOGIN", loginRule);
    }
}