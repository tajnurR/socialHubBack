package com.socialhub.socialhubBackend.integration.core.dto;

import java.time.Instant;

/** API representation of a post fetched from a platform. */
public record IntegrationPostResponse(
        String id, String message, Instant createdTime, String fullPicture, String permalinkUrl) {}
