package com.socialhub.socialhubBackend.integration.linkedin;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInClient.TokenResponse;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInClient.UserInfoResponse;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInOAuthStateStore.StateEntry;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInAppCredentials;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialService;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.AuthorizationUrlResponse;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.net.URI;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LinkedInOAuthService {

    private static final String TOKEN_TYPE = "LINKEDIN_OAUTH";

    private final LinkedInCredentialService credentialService;
    private final LinkedInOAuthStateStore stateStore;
    private final LinkedInClient linkedInClient;
    private final LinkedInProperties properties;
    private final IntegrationService integrationService;
    private final CurrentUserProvider currentUserProvider;

    public LinkedInOAuthService(
            LinkedInCredentialService credentialService,
            LinkedInOAuthStateStore stateStore,
            LinkedInClient linkedInClient,
            LinkedInProperties properties,
            IntegrationService integrationService,
            CurrentUserProvider currentUserProvider) {
        this.credentialService = credentialService;
        this.stateStore = stateStore;
        this.linkedInClient = linkedInClient;
        this.properties = properties;
        this.integrationService = integrationService;
        this.currentUserProvider = currentUserProvider;
    }

    public AuthorizationUrlResponse authorizationUrl(String redirectUri, Long configId) {
        LinkedInAppCredentials credentials = credentialService.resolve(configId);
        String effectiveRedirectUri = credentials.redirectUri() != null && !credentials.redirectUri().isBlank()
                ? credentials.redirectUri()
                : redirectUri;
        validateRedirectUri(effectiveRedirectUri);
        StateEntry entry = stateStore.create(
                currentUserProvider.currentUser(), credentials.configId(), effectiveRedirectUri);
        String scopes = credentials.scopes() != null && !credentials.scopes().isBlank()
                ? credentials.scopes()
                : properties.joinedScopes();
        String url = UriComponentsBuilder.fromUriString(properties.authBaseUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", credentials.clientId())
                .queryParam("redirect_uri", effectiveRedirectUri)
                .queryParam("state", entry.state())
                .queryParam("scope", scopes)
                .build()
                .encode()
                .toUriString();
        return new AuthorizationUrlResponse(url, entry.state(), entry.expiresAt());
    }

    public IntegrationResponse connectWithAuthorizationCode(String code, String state) {
        StateEntry entry = stateStore.consume(state, currentUserProvider.currentUser());
        LinkedInAppCredentials credentials = credentialService.resolve(entry.configId());
        TokenResponse token = linkedInClient.exchangeAuthorizationCode(
                code,
                entry.redirectUri(),
                credentials.clientId(),
                credentials.clientSecret());
        if (token.accessToken() == null || token.accessToken().isBlank()) {
            throw new BusinessException("LinkedIn OAuth failed: no access token was returned.");
        }
        UserInfoResponse profile = linkedInClient.getUserInfo(token.accessToken());
        if (profile.sub() == null || profile.sub().isBlank()) {
            throw new BusinessException("LinkedIn OAuth failed: no profile id was returned.");
        }
        String authorUrn = profile.sub().startsWith("urn:li:")
                ? profile.sub()
                : "urn:li:person:" + profile.sub();
        ProviderAccount account = new ProviderAccount(
                SocialPlatform.LINKEDIN,
                authorUrn,
                displayName(profile, profile.sub()),
                token.accessToken());
        Instant expiresAt = token.expiresIn() != null && token.expiresIn() > 0
                ? Instant.now().plusSeconds(token.expiresIn())
                : null;
        return integrationService.persistConnection(
                SocialPlatform.LINKEDIN,
                account,
                TOKEN_TYPE,
                expiresAt,
                credentials.configId());
    }

    private String displayName(UserInfoResponse profile, String fallbackId) {
        if (profile.name() != null && !profile.name().isBlank()) {
            return profile.name();
        }
        String combined = ((profile.givenName() == null ? "" : profile.givenName()) + " "
                        + (profile.familyName() == null ? "" : profile.familyName()))
                .trim();
        return combined.isBlank() ? "LinkedIn profile " + fallbackId : combined;
    }

    private void validateRedirectUri(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            if (uri.getScheme() == null || uri.getHost() == null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid LinkedIn redirect URI.");
        }
    }
}
