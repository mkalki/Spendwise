package com.spendwise.integration.gemini.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeminiRequest(
        @NotBlank
        @Size(max = 10000)
        @Schema(description = "Prompt to send to Gemini", example = "Explain how spending limits work.")
        String prompt) {
}
