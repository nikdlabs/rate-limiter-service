package com.ratelimiter.controller;

import com.ratelimiter.model.CheckRequest;
import com.ratelimiter.model.CheckResponse;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RuleConfig;
import com.ratelimiter.model.UsageInfo;
import com.ratelimiter.service.RateLimiterService;
import com.ratelimiter.service.RuleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RateLimiterController {

    private final RateLimiterService rateLimiterService;
    private final RuleService ruleService;

    public RateLimiterController(RateLimiterService rateLimiterService, RuleService ruleService) {
        this.rateLimiterService = rateLimiterService;
        this.ruleService = ruleService;
    }

    @PostMapping("/check")
    public ResponseEntity<CheckResponse> check(@RequestBody CheckRequest request) {
        RateLimitResult result = rateLimiterService.check(request.getUserId(), request.getAction());

        if (result.isAllowed()) {
            return ResponseEntity.ok(CheckResponse.allow(result));
        }
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(CheckResponse.deny(result, request.getAction()));
    }

    @PostMapping("/rules")
    public ResponseEntity<RuleConfig> createRule(@RequestBody RuleConfig rule) {
        ruleService.saveRule(rule);
        return ResponseEntity.status(HttpStatus.CREATED).body(rule);
    }

    @GetMapping("/rules")
    public ResponseEntity<List<RuleConfig>> getAllRules() {
        return ResponseEntity.ok(ruleService.getAllRules());
    }

    @GetMapping("/usage/{userId}")
    public ResponseEntity<List<UsageInfo>> getUsage(@PathVariable String userId) {
        return ResponseEntity.ok(rateLimiterService.getUsage(userId));
    }

    @DeleteMapping("/usage/{userId}")
    public ResponseEntity<Void> resetUsage(@PathVariable String userId) {
        rateLimiterService.resetUser(userId);
        return ResponseEntity.noContent().build();
    }
}