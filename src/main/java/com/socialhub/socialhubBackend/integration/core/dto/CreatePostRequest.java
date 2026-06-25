package com.socialhub.socialhubBackend.integration.core.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to publish a post. {@code link} is optional; media/scheduling fields
 * can be added later without changing the endpoint shape.
 */
public record CreatePostRequest(@NotBlank String message, String link) {}
