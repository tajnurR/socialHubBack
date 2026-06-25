package com.socialhub.socialhubBackend.integration.core.dto;

import com.socialhub.socialhubBackend.integration.core.SocialPlatform;

/** Lightweight view of a registered provider, for the integrations endpoint. */
public record ProviderInfo(SocialPlatform platform, boolean enabled) {}
