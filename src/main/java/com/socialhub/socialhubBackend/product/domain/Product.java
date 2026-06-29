package com.socialhub.socialhubBackend.product.domain;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Demo product catalog entry. Owned per user; a {@code Post} may reference the
 * product it is about ({@code Post.productId}).
 */
@Getter
@Setter
@Entity
@Table(name = "products")
public class Product extends TenantBaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 100)
    private String sku;

    @Column(columnDefinition = "text")
    private String description;
}
