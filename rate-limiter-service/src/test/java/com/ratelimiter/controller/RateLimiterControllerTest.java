package com.ratelimiter.controller;

import com.ratelimiter.model.CheckRequest;
import com.ratelimiter.model.RateLimitResult;
import com.ratelimiter.model.RuleConfig;
import com.ratelimiter.model.UsageInfo;
import com.ratelimiter.service.RateLimiterService;
import com.ratelimiter.service.RuleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RateLimiterController.class)
class RateLimiterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RateLimiterService rateLimiterService;

    @MockitoBean
    private RuleService ruleService;

    @Test
    @DisplayName("POST /check — allowed request returns 200 with allow-shaped JSON")
    void check_allowed_returns200() throws Exception {
        RateLimitResult result = new RateLimitResult(true, 4, 42L, "sliding_window");
        when(rateLimiterService.check("user123", "OTP")).thenReturn(result);

        CheckRequest request = new CheckRequest("user123", "OTP");

        mockMvc.perform(post("/api/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.remaining").value(4))
                .andExpect(jsonPath("$.resetAfter").value(42))
                .andExpect(jsonPath("$.algorithm").value("sliding_window"))
                .andExpect(jsonPath("$.retryAfter").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    @DisplayName("POST /check — denied request returns 429 with deny-shaped JSON")
    void check_denied_returns429() throws Exception {
        RateLimitResult result = new RateLimitResult(false, 0, 15L, "sliding_window");
        when(rateLimiterService.check("user123", "OTP")).thenReturn(result);

        CheckRequest request = new CheckRequest("user123", "OTP");

        mockMvc.perform(post("/api/v1/check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.retryAfter").value(15))
                .andExpect(jsonPath("$.reason").value("OTP limit exceeded"))
                .andExpect(jsonPath("$.resetAfter").doesNotExist())
                .andExpect(jsonPath("$.algorithm").doesNotExist());
    }

    @Test
    @DisplayName("POST /rules — creates a rule and returns 201")
    void createRule_returns201() throws Exception {
        RuleConfig rule = new RuleConfig("OTP", 5, 60, "sliding_window");

        mockMvc.perform(post("/api/v1/rules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rule)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.action").value("OTP"))
                .andExpect(jsonPath("$.limit").value(5));

        verify(ruleService).saveRule(rule);
    }

    @Test
    @DisplayName("GET /rules — returns all configured rules")
    void getAllRules_returns200WithList() throws Exception {
        RuleConfig rule = new RuleConfig("OTP", 5, 60, "sliding_window");
        when(ruleService.getAllRules()).thenReturn(List.of(rule));

        mockMvc.perform(get("/api/v1/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("OTP"))
                .andExpect(jsonPath("$[0].limit").value(5));
    }

    @Test
    @DisplayName("GET /usage/{userId} — returns usage across all configured rules")
    void getUsage_returns200WithList() throws Exception {
        UsageInfo usage = new UsageInfo("OTP", 5, 4, 42L, "sliding_window");
        when(rateLimiterService.getUsage("user123")).thenReturn(List.of(usage));

        mockMvc.perform(get("/api/v1/usage/user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].action").value("OTP"))
                .andExpect(jsonPath("$[0].remaining").value(4));
    }

    @Test
    @DisplayName("DELETE /usage/{userId} — resets the user and returns 204")
    void resetUsage_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/usage/user123"))
                .andExpect(status().isNoContent());

        verify(rateLimiterService).resetUser("user123");
    }
}