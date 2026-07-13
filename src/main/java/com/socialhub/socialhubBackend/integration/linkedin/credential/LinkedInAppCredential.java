package com.socialhub.socialhubBackend.integration.linkedin.credential;

import com.socialhub.socialhubBackend.common.entity.TenantBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "linkedin_app_credentials")
public class LinkedInAppCredential extends TenantBaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 120)
    private String label;

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "client_secret", nullable = false, columnDefinition = "text")
    private String clientSecret;

    @Column(name = "redirect_uri", length = 500)
    private String redirectUri;

    @Column(columnDefinition = "text")
    private String scopes;

    @Column(name = "api_version", length = 20)
    private String apiVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LinkedInAppCredentialStatus status = LinkedInAppCredentialStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
