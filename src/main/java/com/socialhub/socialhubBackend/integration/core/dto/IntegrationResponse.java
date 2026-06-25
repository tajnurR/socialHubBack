package com.socialhub.socialhubBackend.integration.core.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.IntegrationStatus;
import java.time.Instant;

/**
 * API representation of a connected integration. The access token is never
 * included — only a masked placeholder ({@code accessTokenMasked}).
 */
public record IntegrationResponse(
        Long id,
        SocialPlatform platform,
        String externalAccountId,
        String displayName,
        IntegrationStatus status,
        String accessTokenMasked,
        String tokenType,
        Instant tokenObtainedAt,
        Instant tokenExpiresAt,
        Instant createdAt) {}
