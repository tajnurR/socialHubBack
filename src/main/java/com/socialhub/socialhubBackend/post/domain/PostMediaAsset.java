package com.socialhub.socialhubBackend.post.domain;

import com.socialhub.socialhubBackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "post_media_assets",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_post_media_assets_post_media",
                columnNames = {"post_id", "media_asset_id"}))
public class PostMediaAsset extends BaseEntity {

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "media_asset_id", nullable = false)
    private Long mediaAssetId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;
}
