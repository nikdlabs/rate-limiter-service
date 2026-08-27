package com.ratelimiter.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsageInfo {
    private String action;
    private int limit;
    private int remaining;
    private long resetAfterSeconds;
    private String algorithm;
}