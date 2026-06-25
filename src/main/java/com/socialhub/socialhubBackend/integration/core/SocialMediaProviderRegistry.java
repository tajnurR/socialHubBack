package com.socialhub.socialhubBackend.integration.core;

import com.socialhub.socialhubBackend.common.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Central lookup for {@link SocialMediaProvider} implementations.
 *
 * <p>Spring injects every {@code SocialMediaProvider} bean on the classpath, so
 * the set of supported platforms is wired up automatically at startup.
 */
@Component
public class SocialMediaProviderRegistry {

    private final Map<SocialPlatform, SocialMediaProvider> providers =
            new EnumMap<>(SocialPlatform.class);

    public SocialMediaProviderRegistry(List<SocialMediaProvider> discoveredProviders) {
        for (SocialMediaProvider provider : discoveredProviders) {
            SocialMediaProvider existing = providers.putIfAbsent(provider.platform(), provider);
            if (existing != null) {
                throw new IllegalStateException(
                        "Multiple providers registered for platform " + provider.platform());
            }
        }
    }

    /** Returns the provider for a platform, or 404 if none is registered. */
    public SocialMediaProvider get(SocialPlatform platform) {
        SocialMediaProvider provider = providers.get(platform);
        if (provider == null) {
            throw new ResourceNotFoundException("No provider registered for platform " + platform);
        }
        return provider;
    }

    public Set<SocialPlatform> supportedPlatforms() {
        return providers.keySet();
    }

    public Collection<SocialMediaProvider> all() {
        return providers.values();
    }
}
