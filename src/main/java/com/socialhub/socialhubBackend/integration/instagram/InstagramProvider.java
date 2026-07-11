package com.socialhub.socialhubBackend.integration.instagram;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import com.socialhub.socialhubBackend.post.domain.PostMediaType;
import org.springframework.stereotype.Component;

/**
 * Instagram Business/Creator integration through Instagram Login.
 */
@Component
public class InstagramProvider extends AbstractSocialMediaProvider {

    private final InstagramGraphClient graphClient;

    public InstagramProvider(InstagramGraphClient graphClient) {
        this.graphClient = graphClient;
    }

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.INSTAGRAM;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ProviderPostRef createPost(
            String externalAccountId, String accessToken, CreatePostCommand command) {
        if (command.message() == null || command.message().isBlank()) {
            throw new BusinessException("Instagram caption is required.");
        }
        if (command.mediaType() == null) {
            throw new BusinessException("Instagram posts require an image or video.");
        }
        if (command.mediaUrl() == null || command.mediaUrl().isBlank()) {
            throw new BusinessException(
                    "Instagram requires a publicly accessible image or video URL. "
                            + "Select media with a public URL before publishing.");
        }
        if (command.mediaType() != PostMediaType.IMAGE && command.mediaType() != PostMediaType.VIDEO) {
            throw new BusinessException("Instagram supports image and video posts only.");
        }
        InstagramGraphClient.CreateResponse container = graphClient.createMediaContainer(
                externalAccountId,
                accessToken,
                command.mediaUrl(),
                command.mediaType(),
                command.message(),
                null);
        InstagramGraphClient.CreateResponse published = graphClient.publishMedia(
                externalAccountId,
                accessToken,
                container.id(),
                null);
        return new ProviderPostRef(
                published.id(),
                "Instagram post created: " + published.id());
    }
}
