package com.socialhub.socialhubBackend.integration.instagram;

import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import org.springframework.stereotype.Component;

/**
 * Instagram integration (stub).
 *
 * <p>TODO: implement against the Instagram Graph API.
 */
@Component
public class InstagramProvider extends AbstractSocialMediaProvider {

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.INSTAGRAM;
    }
}
