package com.socialhub.socialhubBackend.integration.core.dto;

import java.util.List;

/** A page of posts plus the cursor to fetch the next page ({@code null} if none). */
public record IntegrationPostPageResponse(List<IntegrationPostResponse> posts, String nextCursor) {}
