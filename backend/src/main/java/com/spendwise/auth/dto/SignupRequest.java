package com.spendwise.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank
                @Size(min = 3, max = 32)
                @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Username can use letters, numbers, '.', '_' and '-'.")
                String username,
        @NotBlank @Size(min = 12, max = 128) String password) {
}
