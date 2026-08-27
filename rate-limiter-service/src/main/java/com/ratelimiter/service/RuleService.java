package com.ratelimiter.service;

import com.ratelimiter.model.RuleConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class RuleService {

    private static final String RULE_KEY_PREFIX = "rule:";
    private static final String RULE_INDEX_KEY = "rule:index";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final int defaultLimit;
    private final int defaultWindowSeconds;
    private final String defaultAlgorithm;

    public RuleService(
            RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper,
            @Value("${ratelimiter.default.limit}") int defaultLimit,
            @Value("${ratelimiter.default.window-seconds}") int defaultWindowSeconds,
            @Value("${ratelimiter.default.algorithm}") String defaultAlgorithm) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.defaultLimit = defaultLimit;
        this.defaultWindowSeconds = defaultWindowSeconds;
        this.defaultAlgorithm = defaultAlgorithm;
    }

    public void saveRule(RuleConfig rule) {
        String json = objectMapper.writeValueAsString(rule);
        redisTemplate.opsForValue().set(RULE_KEY_PREFIX + rule.getAction(), json);
        redisTemplate.opsForSet().add(RULE_INDEX_KEY, rule.getAction());
    }

    public RuleConfig getRule(String action) {
        String json = redisTemplate.opsForValue().get(RULE_KEY_PREFIX + action);
        if (json == null) {
            return buildDefaultRule(action);
        }
        return objectMapper.readValue(json, RuleConfig.class);
    }

    public List<RuleConfig> getAllRules() {
        Set<String> actions = redisTemplate.opsForSet().members(RULE_INDEX_KEY);
        List<RuleConfig> rules = new ArrayList<>();

        if (actions == null) {
            return rules;
        }

        for (String action : actions) {
            rules.add(getRule(action));
        }
        return rules;
    }

    private RuleConfig buildDefaultRule(String action) {
        return new RuleConfig(action, defaultLimit, defaultWindowSeconds, defaultAlgorithm);
    }
}