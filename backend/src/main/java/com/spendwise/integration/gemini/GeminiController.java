package com.spendwise.integration.gemini;

import com.spendwise.integration.gemini.dto.GeminiRequest;
import com.spendwise.integration.gemini.dto.GeminiResponse;
import com.spendwise.common.security.GeminiUserRateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class GeminiController {

    private final GeminiService geminiService;
    private final GeminiUserRateLimiter rateLimiter;

    public GeminiController(GeminiService geminiService, GeminiUserRateLimiter rateLimiter) {
        this.geminiService = geminiService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/generate")
    @Operation(
            summary = "Generate a response with Gemini",
            description = "Sends a prompt to the configured Gemini model. This endpoint is independent of Channel3.")
    public GeminiResponse generate(
            @AuthenticationPrincipal(expression = "name") String ownerId,
            @Valid @RequestBody GeminiRequest request) {
        rateLimiter.acquire(ownerId);
        return geminiService.generate(request);
    }
}
