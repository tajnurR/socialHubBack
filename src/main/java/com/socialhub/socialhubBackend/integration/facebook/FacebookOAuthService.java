package com.socialhub.socialhubBackend.integration.facebook;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.domain.SocialIntegration;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import com.socialhub.socialhubBackend.integration.facebook.FacebookExchangeStore.ExchangeMeta;
import com.socialhub.socialhubBackend.integration.facebook.FacebookExchangeStore.PageToken;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredentialProvider;
import com.socialhub.socialhubBackend.integration.facebook.credential.FacebookAppCredentials;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookOAuthDtos.ExchangeResponse;
import com.socialhub.socialhubBackend.integration.facebook.dto.FacebookOAuthDtos.PageOption;
import com.socialhub.socialhubBackend.integration.facebook.dto.GraphDtos;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Drives the Facebook Login (OAuth) connect flow. The app secret and all token
 * exchange happen here, server-side.
 *
 * <p>Token lifecycle: short-lived user token (from the frontend SDK) →
 * long-lived user token (fb_exchange_token) → {@code /me/accounts} → per-page
 * <b>non-expiring</b> Page access tokens. The resolved Page tokens are held
 * transiently in {@link FacebookExchangeStore} (never returned to the client);
 * the chosen page's token is then persisted (encrypted) via {@link IntegrationService}.
 */
@Service
public class FacebookOAuthService {

    private static final String TOKEN_TYPE = "PAGE_OAUTH";

    private final FacebookAppCredentialProvider appCredentialProvider;
    private final FacebookGraphClient graphClient;
    private final FacebookExchangeStore exchangeStore;
    private final IntegrationService integrationService;
    private final CurrentUserProvider currentUserProvider;

    public FacebookOAuthService(
            FacebookAppCredentialProvider appCredentialProvider,
            FacebookGraphClient graphClient,
            FacebookExchangeStore exchangeStore,
            IntegrationService integrationService,
            CurrentUserProvider currentUserProvider) {
        this.appCredentialProvider = appCredentialProvider;
        this.graphClient = graphClient;
        this.exchangeStore = exchangeStore;
        this.integrationService = integrationService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Exchange the short-lived user token and return the pages the user can connect. */
    public ExchangeResponse exchange(String shortLivedToken) {
        // Resolve the org's own Meta app credentials (throws if not configured yet).
        FacebookAppCredentials credentials = appCredentialProvider.resolve();

        GraphDtos.TokenResponse longLived = graphClient.exchangeForLongLivedUserToken(
                shortLivedToken, credentials.appId(), credentials.appSecret(), credentials.apiVersion());
        GraphDtos.AccountsResponse accounts =
                graphClient.getManagedPages(longLived.accessToken(), credentials.apiVersion());

        List<GraphDtos.Page> data =
                accounts != null && accounts.data() != null ? accounts.data() : List.of();
        List<PageToken> pages = data.stream()
                .filter(p -> p.id() != null && p.accessToken() != null && !p.accessToken().isBlank())
                .map(p -> new PageToken(p.id(), p.name(), p.accessToken()))
                .toList();
        if (pages.isEmpty()) {
            throw new BusinessException(
                    "No Facebook Pages were returned for this account. Make sure you granted "
                            + "access to a Page and the pages_show_list permission.");
        }

        String exchangeId = exchangeStore.put(
                currentUserProvider.currentUser().userId(),
                credentials.configId(),
                credentials.apiVersion(),
                pages);
        List<PageOption> options = pages.stream()
                .map(p -> new PageOption(p.pageId(), p.name()))
                .toList();
        Instant userTokenExpiresAt = longLived.expiresIn() != null && longLived.expiresIn() > 0
                ? Instant.now().plusSeconds(longLived.expiresIn())
                : null;
        return new ExchangeResponse(exchangeId, options, userTokenExpiresAt);
    }

    /** Persist the chosen page from a prior exchange (validates the page token first). */
    public IntegrationResponse connect(String exchangeId, String pageId) {
        ExchangeMeta meta = metaOrThrow(exchangeId);
        PageToken page = resolveOrThrow(exchangeId, pageId);
        ProviderAccount account = validateAndBuildAccount(page, meta.apiVersion());
        // Page tokens from a long-lived user token do not expire → expiresAt = null.
        // Link the connection to the app config it was created through.
        return integrationService.persistConnection(
                SocialPlatform.FACEBOOK, account, TOKEN_TYPE, null, meta.configId());
    }

    /** Re-authenticate an existing integration in place using a fresh exchange. */
    public IntegrationResponse reauth(Long integrationId, String exchangeId) {
        SocialIntegration integration = integrationService.getOwnedIntegration(integrationId);
        if (integration.getPlatform() != SocialPlatform.FACEBOOK) {
            throw new BusinessException("This integration is not a Facebook integration.");
        }
        ExchangeMeta meta = metaOrThrow(exchangeId);
        PageToken page = resolveOrThrow(exchangeId, integration.getExternalAccountId());
        validateAndBuildAccount(page, meta.apiVersion());
        return integrationService.reauth(integrationId, page.accessToken(), TOKEN_TYPE, null);
    }

    private ExchangeMeta metaOrThrow(String exchangeId) {
        ExchangeMeta meta = exchangeStore.meta(exchangeId)
                .orElseThrow(() -> new BusinessException(
                        "Your Facebook session expired. Please connect with Facebook again."));
        // Isolation: an exchange may only be completed by the user who started it.
        if (!meta.userId().equals(currentUserProvider.currentUser().userId())) {
            throw new BusinessException(
                    "Your Facebook session expired. Please connect with Facebook again.");
        }
        return meta;
    }

    private PageToken resolveOrThrow(String exchangeId, String pageId) {
        return exchangeStore.resolve(exchangeId, pageId)
                .orElseThrow(() -> new BusinessException(
                        "Your Facebook selection expired or this account doesn't manage that Page. "
                                + "Please connect with Facebook again."));
    }

    private ProviderAccount validateAndBuildAccount(PageToken page, String apiVersion) {
        GraphDtos.Page validated = graphClient.getPage(page.pageId(), page.accessToken(), apiVersion);
        String name = validated.name() != null ? validated.name() : page.name();
        return new ProviderAccount(SocialPlatform.FACEBOOK, page.pageId(), name, page.accessToken());
    }
}
