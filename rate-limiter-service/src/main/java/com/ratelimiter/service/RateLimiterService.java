package com.ratelimiter.service;

import com.ratelimiter.algorithm.RateLimitAlgorithm;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RuleConfig;
import org.springframework.stereotype.Service;
import com.ratelimiter.model.UsageInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class RateLimiterService {

    private final RuleService ruleService;
    private final Map<String, RateLimitAlgorithm> algorithms;

    public RateLimiterService(RuleService ruleService, Map<String, RateLimitAlgorithm> algorithms) {
        this.ruleService = ruleService;
        this.algorithms = algorithms;
    }

    public RateLimitResult check(String userId, String action) {
        RuleConfig rule = ruleService.getRule(action);
        RateLimitAlgorithm algorithm = resolveAlgorithm(rule.getAlgorithm());

        boolean allowed = algorithm.isAllowed(userId, action, rule);
        int remaining = algorithm.getRemaining(userId, action, rule);
        long resetAfterSeconds = algorithm.getResetAfterSeconds(userId, action, rule);

        return new RateLimitResult(allowed, remaining, resetAfterSeconds, rule.getAlgorithm());
    }

    private RateLimitAlgorithm resolveAlgorithm(String algorithmName) {
        RateLimitAlgorithm algorithm = algorithms.get(algorithmName);
        if (algorithm == null) {
            throw new IllegalArgumentException("Unknown rate limit algorithm: " + algorithmName);
        }
        return algorithm;
    }

    public List<UsageInfo> getUsage(String userId) {
        List<RuleConfig> rules = ruleService.getAllRules();
        List<UsageInfo> usage = new ArrayList<>();

        for (RuleConfig rule : rules) {
            RateLimitAlgorithm algorithm = resolveAlgorithm(rule.getAlgorithm());
            int remaining = algorithm.getRemaining(userId, rule.getAction(), rule);
            long resetAfter = algorithm.getResetAfterSeconds(userId, rule.getAction(), rule);
            usage.add(new UsageInfo(rule.getAction(), rule.getLimit(), remaining, resetAfter, rule.getAlgorithm()));
        }
        return usage;
    }

    public void resetUser(String userId) {
        List<RuleConfig> rules = ruleService.getAllRules();
        for (RuleConfig rule : rules) {
            RateLimitAlgorithm algorithm = resolveAlgorithm(rule.getAlgorithm());
            algorithm.reset(userId, rule.getAction(), rule);
        }
    }
}