package com.socialhub.socialhubBackend.integration.core.dto;

/** Result of creating a post: the platform's id for the new post. */
public record CreatePostResponse(String externalPostId) {}
