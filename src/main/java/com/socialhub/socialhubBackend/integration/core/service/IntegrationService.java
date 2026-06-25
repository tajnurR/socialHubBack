package com.socialhub.socialhubBackend.integration.core.service;

import com.socialhub.socialhubBackend.integration.core.SocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialMediaProviderRegistry;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderInfo;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** Exposes the registered integrations; thin facade over the provider registry. */
@Service
public class IntegrationService {

    private final SocialMediaProviderRegistry registry;

    public IntegrationService(SocialMediaProviderRegistry registry) {
        this.registry = registry;
    }

    public List<ProviderInfo> listProviders() {
        return registry.all().stream()
                .map(provider -> new ProviderInfo(provider.platform(), provider.isEnabled()))
                .sorted(Comparator.comparing(p -> p.platform().name()))
                .toList();
    }

    /** Convenience for callers that need the live provider for a platform. */
    public SocialMediaProvider provider(
            com.socialhub.socialhubBackend.integration.core.SocialPlatform platform) {
        return registry.get(platform);
    }
}
