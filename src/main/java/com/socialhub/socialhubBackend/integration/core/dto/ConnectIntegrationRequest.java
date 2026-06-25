package com.socialhub.socialhubBackend.integration.core.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request to connect a platform. {@code credentials} are platform-specific
 * key/value pairs (e.g. {@code pageId}, {@code accessToken} for Facebook) — the
 * provider knows how to interpret them, keeping this DTO platform-agnostic.
 */
public record ConnectIntegrationRequest(
        @NotNull SocialPlatform platform,
        @NotEmpty Map<String, String> credentials) {}
