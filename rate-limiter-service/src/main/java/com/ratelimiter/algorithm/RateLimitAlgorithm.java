package com.ratelimiter.algorithm;

import com.ratelimiter.model.RuleConfig;

public interface RateLimitAlgorithm {

    boolean isAllowed(String userId, String action, RuleConfig rule);

    int getRemaining(String userId, String action, RuleConfig rule);

    long getResetAfterSeconds(String userId, String action, RuleConfig rule);

    void reset(String userId, String action, RuleConfig rule);
}