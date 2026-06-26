package com.socialhub.socialhubBackend.auth.web;

import com.socialhub.socialhubBackend.auth.AuthService;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.AuthResponse;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.LoginRequest;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.RefreshRequest;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.RegisterRequest;
import com.socialhub.socialhubBackend.auth.dto.AuthDtos.UserProfile;
import com.socialhub.socialhubBackend.auth.token.TokenService;
import com.socialhub.socialhubBackend.auth.token.TokenService.AuthTokens;
import com.socialhub.socialhubBackend.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints. {@code register}/{@code login}/{@code refresh} are
 * public; {@code me}/{@code logout} require a valid access token.
 *
 * <p>Logout is client-side for stateless JWTs (the client drops the tokens);
 * the endpoint exists for symmetry and future server-side revocation.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Username/password authentication")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account and return tokens")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request), "Registered");
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email + password")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a fresh token pair")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        AuthTokens tokens = tokenService.refresh(request.refreshToken());
        return ApiResponse.ok(new AuthResponse(
                tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds(), null));
    }

    @GetMapping("/me")
    @Operation(summary = "Current authenticated user's profile")
    public ApiResponse<UserProfile> me() {
        return ApiResponse.ok(authService.currentProfile());
    }

    @PostMapping("/logout")
    @Operation(summary = "Client-side logout (drop tokens); no-op server-side")
    public ApiResponse<Void> logout() {
        return ApiResponse.ok(null, "Logged out");
    }
}
