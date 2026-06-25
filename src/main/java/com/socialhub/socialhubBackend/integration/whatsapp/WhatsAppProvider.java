package com.socialhub.socialhubBackend.integration.whatsapp;

import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import org.springframework.stereotype.Component;

/**
 * WhatsApp integration (stub).
 *
 * <p>TODO: implement against the WhatsApp Business Cloud API.
 */
@Component
public class WhatsAppProvider extends AbstractSocialMediaProvider {

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.WHATSAPP;
    }

    @Override
    public boolean isEnabled() {
        return false; // "coming soon" — not selectable yet
    }
}
