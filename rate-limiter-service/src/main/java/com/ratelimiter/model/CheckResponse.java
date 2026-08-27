package com.ratelimiter.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckResponse {
    private boolean allowed;
    private int remaining;
    private Long resetAfter;
    private Long retryAfter;
    private String reason;
    private String algorithm;

    public static CheckResponse allow(RateLimitResult result) {
        CheckResponse response = new CheckResponse();
        response.setAllowed(true);
        response.setRemaining(result.getRemaining());
        response.setResetAfter(result.getResetAfterSeconds());
        response.setAlgorithm(result.getAlgorithm());
        return response;
    }

    public static CheckResponse deny(RateLimitResult result, String action) {
        CheckResponse response = new CheckResponse();
        response.setAllowed(false);
        response.setRemaining(result.getRemaining());
        response.setRetryAfter(result.getResetAfterSeconds());
        response.setReason(action + " limit exceeded");
        return response;
    }
}