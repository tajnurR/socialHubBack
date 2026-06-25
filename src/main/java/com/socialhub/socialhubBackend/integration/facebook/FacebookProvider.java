package com.socialhub.socialhubBackend.integration.facebook;

import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import org.springframework.stereotype.Component;

/**
 * Facebook integration (stub).
 *
 * <p>TODO: implement against the Facebook Graph API. Override the methods from
 * {@link AbstractSocialMediaProvider} one at a time; until then they report
 * "not implemented".
 */
@Component
public class FacebookProvider extends AbstractSocialMediaProvider {

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.FACEBOOK;
    }
}
