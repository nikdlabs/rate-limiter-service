package com.demootp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OtpCheckResult {
    private boolean allowed;
    private int remaining;
    private Long resetAfter;
    private Long retryAfter;
    private String reason;
    private String algorithm;
}