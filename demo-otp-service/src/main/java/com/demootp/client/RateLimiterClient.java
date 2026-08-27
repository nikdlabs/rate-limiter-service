package com.demootp.client;

import com.demootp.model.OtpCheckResult;
import com.demootp.model.RateLimitCheckRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class RateLimiterClient {

    private final RestClient restClient;

    public RateLimiterClient(@Value("${ratelimiter.base-url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    public OtpCheckResult check(String userId, String action) {
        RateLimitCheckRequest request = new RateLimitCheckRequest(userId, action);

        return restClient.post()
                .uri("/api/v1/check")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, response) -> response.bodyTo(OtpCheckResult.class));
    }
}