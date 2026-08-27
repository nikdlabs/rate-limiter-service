package com.demootp.controller;

import com.demootp.client.RateLimiterClient;
import com.demootp.model.OtpCheckResult;
import com.demootp.model.OtpSendRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo/otp")
public class OtpController {

    private final RateLimiterClient rateLimiterClient;

    public OtpController(RateLimiterClient rateLimiterClient) {
        this.rateLimiterClient = rateLimiterClient;
    }

    @PostMapping("/send")
    public ResponseEntity<String> sendOtp(@RequestBody OtpSendRequest request) {
        OtpCheckResult result = rateLimiterClient.check(request.getUserId(), "OTP");

        if (result.isAllowed()) {
            return ResponseEntity.ok("OTP sent to " + request.getUserId());
        }
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body("Too many attempts. Try again in " + result.getRetryAfter() + " seconds.");
    }
}