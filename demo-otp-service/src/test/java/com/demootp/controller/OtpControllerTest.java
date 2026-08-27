package com.demootp.controller;

import com.demootp.client.RateLimiterClient;
import com.demootp.model.OtpCheckResult;
import com.demootp.model.OtpSendRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OtpController.class)
class OtpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RateLimiterClient rateLimiterClient;

    @Test
    @DisplayName("POST /demo/otp/send — allowed, returns 200 with confirmation message")
    void sendOtp_allowed_returns200() throws Exception {
        OtpCheckResult result = new OtpCheckResult(true, 4, 42L, null, null, "sliding_window");
        when(rateLimiterClient.check("user123", "OTP")).thenReturn(result);

        OtpSendRequest request = new OtpSendRequest("user123");

        mockMvc.perform(post("/demo/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("OTP sent to user123"));
    }

    @Test
    @DisplayName("POST /demo/otp/send — denied, returns 429 with retry message")
    void sendOtp_denied_returns429() throws Exception {
        OtpCheckResult result = new OtpCheckResult(false, 0, null, 15L, "OTP limit exceeded", null);
        when(rateLimiterClient.check("user123", "OTP")).thenReturn(result);

        OtpSendRequest request = new OtpSendRequest("user123");

        mockMvc.perform(post("/demo/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().string("Too many attempts. Try again in 15 seconds."));
    }
}