package com.ratelimiter.algorithm;

import com.ratelimiter.model.RuleConfig;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.List;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

@Component("sliding_window")
public class SlidingWindowAlgorithm implements RateLimitAlgorithm {

    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;

    public SlidingWindowAlgorithm(RedisTemplate<String, String> redisTemplate, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.clock = clock;
    }

    private long nowSeconds() {
        return clock.millis() / 1000L;
    }

    private long currentBucket(RuleConfig rule) {
        return nowSeconds() / rule.getWindowSeconds();
    }

    private long elapsedInCurrentWindow(RuleConfig rule) {
        return nowSeconds() % rule.getWindowSeconds();
    }

    private String bucketKey(String userId, String action, long bucket) {
        return "rl:" + userId + ":" + action + ":" + bucket;
    }

    private double weightedCount(String userId, String action, RuleConfig rule, long bucket, long currentCount) {
        String previousKey = bucketKey(userId, action, bucket - 1);
        String previousVal = redisTemplate.opsForValue().get(previousKey);
        long previousCount = (previousVal == null) ? 0 : Long.parseLong(previousVal);

        double fractionElapsed = elapsedInCurrentWindow(rule) / (double) rule.getWindowSeconds();
        double weightOfPrevious = 1.0 - fractionElapsed;

        return (previousCount * weightOfPrevious) + currentCount;
    }

    @Override
    public boolean isAllowed(String userId, String action, RuleConfig rule) {
        long bucket = currentBucket(rule);
        String currentKey = bucketKey(userId, action, bucket);

        Long currentCount = redisTemplate.opsForValue().increment(currentKey);
        if (currentCount == 1) {
            redisTemplate.expire(currentKey, rule.getWindowSeconds() * 2L, TimeUnit.SECONDS);
        }

        double estimated = weightedCount(userId, action, rule, bucket, currentCount);
        return estimated <= rule.getLimit();
    }

    @Override
    public int getRemaining(String userId, String action, RuleConfig rule) {
        long bucket = currentBucket(rule);
        String currentKey = bucketKey(userId, action, bucket);

        String currentVal = redisTemplate.opsForValue().get(currentKey);
        long currentCount = (currentVal == null) ? 0 : Long.parseLong(currentVal);

        double estimated = weightedCount(userId, action, rule, bucket, currentCount);
        return (int) Math.max(0, Math.floor(rule.getLimit() - estimated));
    }

    @Override
    public long getResetAfterSeconds(String userId, String action, RuleConfig rule) {
        return rule.getWindowSeconds() - elapsedInCurrentWindow(rule);
    }

    @Override
    public void reset(String userId, String action, RuleConfig rule) {
        long bucket = currentBucket(rule);
        String currentKey = bucketKey(userId, action, bucket);
        String previousKey = bucketKey(userId, action, bucket - 1);
        redisTemplate.delete(List.of(currentKey, previousKey));
    }
}