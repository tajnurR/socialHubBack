package com.socialhub.socialhubBackend.integration.linkedin;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import org.springframework.stereotype.Component;

@Component
public class LinkedInProvider extends AbstractSocialMediaProvider {

    private final LinkedInClient linkedInClient;
    private final LinkedInProperties properties;

    public LinkedInProvider(LinkedInClient linkedInClient, LinkedInProperties properties) {
        this.linkedInClient = linkedInClient;
        this.properties = properties;
    }

    @Override
    public SocialPlatform platform() {
        return SocialPlatform.LINKEDIN;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public ProviderPostRef createPost(
            String externalAccountId, String accessToken, CreatePostCommand command) {
        if (command.message() == null || command.message().isBlank()) {
            throw new BusinessException("LinkedIn post content is required.");
        }
        if (command.mediaType() != null || command.mediaBytes() != null || command.mediaUrl() != null) {
            throw new BusinessException(
                    "LinkedIn media publishing is not enabled yet. Create a text-only LinkedIn post for now.");
        }
        String commentary = withOptionalLink(command.message(), command.link());
        LinkedInClient.CreatePostResponse response =
                linkedInClient.createTextPost(externalAccountId, accessToken, commentary, properties.apiVersion());
        return new ProviderPostRef(response.id(), "LinkedIn post created: " + response.id());
    }

    private String withOptionalLink(String message, String link) {
        if (link == null || link.isBlank()) {
            return message;
        }
        return message + "\n\n" + link.trim();
    }
}
