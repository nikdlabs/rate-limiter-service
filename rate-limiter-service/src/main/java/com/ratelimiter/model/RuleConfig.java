package com.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleConfig {
    private String action;
    private int limit;
    private int windowSeconds;
    private String algorithm;
}