package com.socialhub.socialhubBackend.user.context;

/**
 * The identity driving the current request: the user and the organization they
 * belong to. Resolved by {@link CurrentUserProvider}.
 */
public record CurrentUser(Long userId, Long organizationId) {}
