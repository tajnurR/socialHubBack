package com.socialhub.socialhubBackend.integration.linkedin;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.AbstractSocialMediaProvider;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.CreatePostCommand;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderPostRef;
import com.socialhub.socialhubBackend.post.domain.PostMediaType;
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
        String commentary = withOptionalLink(command.message(), command.link());
        String mediaUrn = null;
        if (command.mediaType() != null) {
            mediaUrn = uploadMedia(externalAccountId, accessToken, command);
        }
        LinkedInClient.CreatePostResponse response = linkedInClient.createPost(
                externalAccountId,
                accessToken,
                commentary,
                mediaUrn,
                command.mediaFilename(),
                properties.apiVersion());
        return new ProviderPostRef(response.id(), "LinkedIn post created: " + response.id());
    }

    private String uploadMedia(String ownerUrn, String accessToken, CreatePostCommand command) {
        if (!hasBinaryMedia(command)) {
            throw new BusinessException(
                    "LinkedIn media publishing requires a Media Library file. Select or upload media before publishing.");
        }
        if (command.mediaType() == PostMediaType.IMAGE) {
            return linkedInClient.uploadImage(
                    ownerUrn,
                    accessToken,
                    command.mediaFilename(),
                    command.mediaContentType(),
                    command.mediaBytes(),
                    properties.apiVersion());
        }
        if (command.mediaType() == PostMediaType.VIDEO) {
            return linkedInClient.uploadVideo(
                    ownerUrn,
                    accessToken,
                    command.mediaFilename(),
                    command.mediaContentType(),
                    command.mediaBytes(),
                    properties.apiVersion());
        }
        throw new BusinessException("LinkedIn supports image and MP4 video posts only.");
    }

    private boolean hasBinaryMedia(CreatePostCommand command) {
        return command.mediaBytes() != null
                && command.mediaBytes().length > 0
                && command.mediaFilename() != null
                && !command.mediaFilename().isBlank();
    }

    private String withOptionalLink(String message, String link) {
        if (link == null || link.isBlank()) {
            return message;
        }
        return message + "\n\n" + link.trim();
    }
}
