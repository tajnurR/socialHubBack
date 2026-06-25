package com.socialhub.socialhubBackend.tenant.dto;

import com.socialhub.socialhubBackend.tenant.domain.OrganizationStatus;
import java.time.Instant;

/** API representation of an {@code Organization}, decoupled from the entity. */
public record OrganizationResponse(
        Long id,
        String name,
        String slug,
        OrganizationStatus status,
        Instant createdAt) {}
