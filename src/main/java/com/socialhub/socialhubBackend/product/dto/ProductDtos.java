package com.socialhub.socialhubBackend.product.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/** Request/response DTOs for the demo product catalog. */
public final class ProductDtos {

    private ProductDtos() {}

    public record ProductRequest(@NotBlank String name, String sku, String description) {}

    public record ProductResponse(
            Long id, String name, String sku, String description, Instant createdAt) {}
}
