package com.socialhub.socialhubBackend.auth.dto;

import com.socialhub.socialhubBackend.user.domain.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response DTOs for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @NotBlank String displayName) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    /** The authenticated user's profile (no secrets). */
    public record UserProfile(
            Long id, String email, String displayName, UserRole role, Long organizationId) {}

    public record AuthResponse(
            String accessToken, String refreshToken, long expiresIn, UserProfile user) {}
}
