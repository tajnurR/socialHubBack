package com.socialhub.socialhubBackend.integration.instagram;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import com.socialhub.socialhubBackend.integration.facebook.FacebookGraphClient;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredentialProvider;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredentials;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos;
import com.socialhub.socialhubBackend.integration.instagram.InstagramGraphClient.ProfileResponse;
import com.socialhub.socialhubBackend.integration.instagram.InstagramGraphClient.TokenResponse;
import com.socialhub.socialhubBackend.integration.instagram.InstagramExchangeStore.ExchangeMeta;
import com.socialhub.socialhubBackend.integration.instagram.InstagramExchangeStore.InstagramAccountToken;
import com.socialhub.socialhubBackend.integration.instagram.InstagramOAuthStateStore.StateEntry;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.AuthorizationUrlResponse;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.ExchangeResponse;
import com.socialhub.socialhubBackend.integration.instagram.dto.InstagramOAuthDtos.InstagramAccountOption;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class InstagramOAuthService {

    private static final String TOKEN_TYPE = "INSTAGRAM_PAGE_OAUTH";
    private static final String DIRECT_TOKEN_TYPE = "INSTAGRAM_OAUTH";

    private final FacebookAppCredentialProvider appCredentialProvider;
    private final FacebookGraphClient graphClient;
    private final InstagramProperties properties;
    private final InstagramOAuthStateStore stateStore;
    private final InstagramGraphClient instagramGraphClient;
    private final InstagramCredentialService credentialService;
    private final InstagramExchangeStore exchangeStore;
    private final IntegrationService integrationService;
    private final CurrentUserProvider currentUserProvider;

    public InstagramOAuthService(
            FacebookAppCredentialProvider appCredentialProvider,
            FacebookGraphClient graphClient,
            InstagramProperties properties,
            InstagramOAuthStateStore stateStore,
            InstagramGraphClient instagramGraphClient,
            InstagramCredentialService credentialService,
            InstagramExchangeStore exchangeStore,
            IntegrationService integrationService,
            CurrentUserProvider currentUserProvider) {
        this.appCredentialProvider = appCredentialProvider;
        this.graphClient = graphClient;
        this.properties = properties;
        this.stateStore = stateStore;
        this.instagramGraphClient = instagramGraphClient;
        this.credentialService = credentialService;
        this.exchangeStore = exchangeStore;
        this.integrationService = integrationService;
        this.currentUserProvider = currentUserProvider;
    }

    public AuthorizationUrlResponse authorizationUrl(String redirectUri, Long configId) {
        InstagramAppCredentials credentials = credentialService.resolve(configId);
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
                .queryParam("client_id", credentials.appId())
                .queryParam("redirect_uri", effectiveRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scopes)
                .queryParam("state", entry.state())
                .queryParam("enable_fb_login", "0")
                .queryParam("force_authentication", "1")
                .build()
                .toUriString();
        return new AuthorizationUrlResponse(url, entry.state(), entry.expiresAt());
    }

    public IntegrationResponse connectWithAuthorizationCode(String code, String state) {
        StateEntry entry = stateStore.consume(state, currentUserProvider.currentUser());
        InstagramAppCredentials credentials = credentialService.resolve(entry.configId());
        TokenResponse shortToken = instagramGraphClient.exchangeAuthorizationCode(
                code, entry.redirectUri(), credentials.appId(), credentials.appSecret());
        if (shortToken.accessToken() == null || shortToken.accessToken().isBlank()) {
            throw new BusinessException("Instagram OAuth failed: no access token was returned.");
        }
        TokenResponse longToken = instagramGraphClient.exchangeForLongLivedToken(
                shortToken.accessToken(), credentials.appSecret());
        String accessToken = longToken.accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException("Instagram OAuth failed: no long-lived access token was returned.");
        }

        ProfileResponse profile = instagramGraphClient.getProfile(accessToken, credentials.apiVersion());
        String accountId = profile.id() != null && !profile.id().isBlank()
                ? profile.id()
                : shortToken.userId();
        if (accountId == null || accountId.isBlank()) {
            throw new BusinessException("Instagram OAuth failed: no Instagram account id was returned.");
        }
        String displayName = displayName(profile, accountId);
        ProviderAccount providerAccount =
                new ProviderAccount(SocialPlatform.INSTAGRAM, accountId, displayName, accessToken);
        Instant expiresAt = longToken.expiresIn() != null && longToken.expiresIn() > 0
                ? Instant.now().plusSeconds(longToken.expiresIn())
                : null;
        return integrationService.persistConnection(
                SocialPlatform.INSTAGRAM,
                providerAccount,
                longToken.tokenType() == null || longToken.tokenType().isBlank()
                        ? DIRECT_TOKEN_TYPE
                        : longToken.tokenType(),
                expiresAt,
                credentials.configId());
    }

    public ExchangeResponse exchange(String shortLivedToken, Long configId) {
        FacebookAppCredentials credentials =
                configId == null ? appCredentialProvider.resolve() : appCredentialProvider.resolveById(configId);
        GraphDtos.TokenResponse longLived = graphClient.exchangeForLongLivedUserToken(
                shortLivedToken, credentials.appId(), credentials.appSecret(), credentials.apiVersion());
        GraphDtos.AccountsResponse accounts =
                graphClient.getManagedPagesWithInstagram(longLived.accessToken(), credentials.apiVersion());
        List<InstagramAccountToken> instagramAccounts = accounts == null || accounts.data() == null
                ? List.of()
                : accounts.data().stream()
                        .filter(page -> page.accessToken() != null && !page.accessToken().isBlank())
                        .filter(page -> page.instagramBusinessAccount() != null)
                        .map(page -> new InstagramAccountToken(
                                page.instagramBusinessAccount().id(),
                                page.instagramBusinessAccount().displayName(),
                                page.id(),
                                page.name(),
                                page.accessToken()))
                        .filter(account -> account.instagramAccountId() != null
                                && !account.instagramAccountId().isBlank())
                        .toList();
        if (instagramAccounts.isEmpty()) {
            throw new BusinessException(
                    "No Instagram professional accounts were returned. Connect an Instagram Business/Creator account "
                            + "to a Facebook Page and approve instagram_basic and instagram_content_publish.");
        }

        String exchangeId = exchangeStore.put(
                currentUserProvider.currentUser().userId(),
                credentials.configId(),
                credentials.apiVersion(),
                instagramAccounts);
        List<InstagramAccountOption> options = instagramAccounts.stream()
                .map(account -> new InstagramAccountOption(
                        account.instagramAccountId(),
                        account.displayName(),
                        account.pageId(),
                        account.pageName()))
                .toList();
        Instant userTokenExpiresAt = longLived.expiresIn() != null && longLived.expiresIn() > 0
                ? Instant.now().plusSeconds(longLived.expiresIn())
                : null;
        return new ExchangeResponse(exchangeId, options, userTokenExpiresAt);
    }

    public IntegrationResponse connect(String exchangeId, String accountId) {
        return connectMany(exchangeId, List.of(accountId)).getFirst();
    }

    public List<IntegrationResponse> connectMany(String exchangeId, List<String> accountIds) {
        ExchangeMeta meta = metaOrThrow(exchangeId);
        List<String> uniqueAccountIds = accountIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        if (uniqueAccountIds.isEmpty()) {
            throw new BusinessException("Select at least one Instagram account to connect.");
        }
        return uniqueAccountIds.stream()
                .map(accountId -> {
                    InstagramAccountToken account = resolveOrThrow(exchangeId, accountId);
                    GraphDtos.InstagramAccount validated = graphClient.getInstagramAccount(
                            account.instagramAccountId(), account.pageAccessToken(), meta.apiVersion());
                    String displayName = validated.displayName() != null
                            ? validated.displayName()
                            : account.displayName();
                    ProviderAccount providerAccount = new ProviderAccount(
                            SocialPlatform.INSTAGRAM,
                            account.instagramAccountId(),
                            displayName,
                            account.pageAccessToken());
                    return integrationService.persistConnection(
                            SocialPlatform.INSTAGRAM, providerAccount, TOKEN_TYPE, null, meta.configId());
                })
                .toList();
    }

    private ExchangeMeta metaOrThrow(String exchangeId) {
        ExchangeMeta meta = exchangeStore.meta(exchangeId)
                .orElseThrow(() -> new BusinessException(
                        "Your Instagram session expired. Please connect with Instagram again."));
        if (!meta.userId().equals(currentUserProvider.currentUser().userId())) {
            throw new BusinessException("Your Instagram session expired. Please connect with Instagram again.");
        }
        return meta;
    }

    private InstagramAccountToken resolveOrThrow(String exchangeId, String accountId) {
        return exchangeStore.resolve(exchangeId, accountId)
                .orElseThrow(() -> new BusinessException(
                        "Your Instagram selection expired or this account is not linked to your Meta profile."));
    }

    private String displayName(ProfileResponse profile, String fallbackId) {
        if (profile.name() != null && !profile.name().isBlank()) {
            return profile.name();
        }
        if (profile.username() != null && !profile.username().isBlank()) {
            return "@" + profile.username();
        }
        return "Instagram account " + fallbackId;
    }

    private void validateRedirectUri(String redirectUri) {
        try {
            URI uri = URI.create(redirectUri);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("Invalid Instagram redirect URI.");
        }
    }
}
