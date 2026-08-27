package com.ratelimiter.algorithm;

import com.ratelimiter.model.RuleConfig;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component("token_bucket")
public class TokenBucketAlgorithm implements RateLimitAlgorithm {

    private final RedisTemplate<String, String> redisTemplate;

    public TokenBucketAlgorithm(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private String buildKey(String userId, String action) {
        return "tb:" + userId + ":" + action;
    }

    private double refillRatePerSecond(RuleConfig rule) {
        return (double) rule.getLimit() / rule.getWindowSeconds();
    }

    private double getRefilledTokens(String key, RuleConfig rule) {
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        String tokensStr = hashOps.get(key, "tokens");
        String lastRefillStr = hashOps.get(key, "lastRefill");

        long now = System.currentTimeMillis() / 1000L;

        if (tokensStr == null || lastRefillStr == null) {
            return rule.getLimit();
        }

        double currentTokens = Double.parseDouble(tokensStr);
        long lastRefill = Long.parseLong(lastRefillStr);
        long elapsedSeconds = now - lastRefill;

        double refilled = currentTokens + (elapsedSeconds * refillRatePerSecond(rule));
        return Math.min(rule.getLimit(), refilled);
    }

    @Override
    public boolean isAllowed(String userId, String action, RuleConfig rule) {
        String key = buildKey(userId, action);
        HashOperations<String, String, String> hashOps = redisTemplate.opsForHash();

        double tokens = getRefilledTokens(key, rule);
        long now = System.currentTimeMillis() / 1000L;

        boolean allowed = tokens >= 1;
        if (allowed) {
            tokens = tokens - 1;
        }

        hashOps.put(key, "tokens", String.valueOf(tokens));
        hashOps.put(key, "lastRefill", String.valueOf(now));

        return allowed;
    }

    @Override
    public int getRemaining(String userId, String action, RuleConfig rule) {
        String key = buildKey(userId, action);
        double tokens = getRefilledTokens(key, rule);
        return (int) Math.floor(tokens);
    }

    @Override
    public long getResetAfterSeconds(String userId, String action, RuleConfig rule) {
        String key = buildKey(userId, action);
        double tokens = getRefilledTokens(key, rule);

        if (tokens >= rule.getLimit()) {
            return 0;
        }
        double tokensNeeded = rule.getLimit() - tokens;
        return (long) Math.ceil(tokensNeeded / refillRatePerSecond(rule));
    }

    @Override
    public void reset(String userId, String action, RuleConfig rule) {
        redisTemplate.delete(buildKey(userId, action));
    }
}