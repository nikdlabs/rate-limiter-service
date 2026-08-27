package com.ratelimiter.service;

import com.ratelimiter.model.RuleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RuleServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private SetOperations<String, String> setOps;

    private RuleService ruleService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOps);

        ruleService = new RuleService(
                redisTemplate,
                new ObjectMapper(),
                10,
                60,
                "sliding_window"
        );
    }

    @Test
    @DisplayName("saveRule — serializes to JSON and adds action to index")
    void saveRule_serializesAndIndexes() {
        RuleConfig rule = new RuleConfig("OTP", 5, 60, "sliding_window");

        ruleService.saveRule(rule);

        verify(valueOps).set(eq("rule:OTP"), contains("\"action\":\"OTP\""));
        verify(setOps).add("rule:index", "OTP");
    }

    @Test
    @DisplayName("getRule — existing rule is deserialized correctly")
    void getRule_existingRule_returnsDeserializedRule() {
        String json = "{\"action\":\"OTP\",\"limit\":5,\"windowSeconds\":60,\"algorithm\":\"sliding_window\"}";
        when(valueOps.get("rule:OTP")).thenReturn(json);

        RuleConfig result = ruleService.getRule("OTP");

        assertEquals("OTP", result.getAction());
        assertEquals(5, result.getLimit());
        assertEquals(60, result.getWindowSeconds());
        assertEquals("sliding_window", result.getAlgorithm());
    }

    @Test
    @DisplayName("getRule — missing rule falls back to configured defaults")
    void getRule_missingRule_returnsDefaultRule() {
        when(valueOps.get("rule:LOGIN")).thenReturn(null);

        RuleConfig result = ruleService.getRule("LOGIN");

        assertEquals("LOGIN", result.getAction());
        assertEquals(10, result.getLimit());
        assertEquals(60, result.getWindowSeconds());
        assertEquals("sliding_window", result.getAlgorithm());
    }

    @Test
    @DisplayName("getAllRules — empty index returns empty list")
    void getAllRules_emptyIndex_returnsEmptyList() {
        when(setOps.members("rule:index")).thenReturn(Set.of());

        List<RuleConfig> result = ruleService.getAllRules();

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("getAllRules — returns all configured rules")
    void getAllRules_withRules_returnsAllDeserialized() {
        when(setOps.members("rule:index")).thenReturn(Set.of("OTP", "LOGIN"));
        when(valueOps.get("rule:OTP"))
                .thenReturn("{\"action\":\"OTP\",\"limit\":5,\"windowSeconds\":60,\"algorithm\":\"sliding_window\"}");
        when(valueOps.get("rule:LOGIN"))
                .thenReturn("{\"action\":\"LOGIN\",\"limit\":3,\"windowSeconds\":300,\"algorithm\":\"token_bucket\"}");

        List<RuleConfig> result = ruleService.getAllRules();

        assertEquals(2, result.size());
    }
}