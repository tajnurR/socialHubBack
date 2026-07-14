package com.socialhub.socialhubBackend.integration.linkedin;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.integration.core.SocialPlatform;
import com.socialhub.socialhubBackend.integration.core.dto.IntegrationResponse;
import com.socialhub.socialhubBackend.integration.core.dto.ProviderDtos.ProviderAccount;
import com.socialhub.socialhubBackend.integration.core.service.IntegrationService;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInClient.TokenResponse;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInClient.UserInfoResponse;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInExchangeStore.Exchange;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInExchangeStore.LinkedInAccountToken;
import com.socialhub.socialhubBackend.integration.linkedin.LinkedInOAuthStateStore.StateEntry;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInAppCredentials;
import com.socialhub.socialhubBackend.integration.linkedin.credential.LinkedInCredentialService;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.AuthorizationUrlResponse;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.ExchangeResponse;
import com.socialhub.socialhubBackend.integration.linkedin.dto.LinkedInOAuthDtos.LinkedInAccountOption;
import com.socialhub.socialhubBackend.user.context.CurrentUserProvider;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class LinkedInOAuthService {

    private static final String TOKEN_TYPE = "LINKEDIN_OAUTH";
    private static final String CONNECTION_PERSONAL = "PERSONAL";
    private static final String CONNECTION_COMPANY = "COMPANY";
    private static final List<String> PERSONAL_SCOPES = List.of(
            "openid", "profile", "email", "w_member_social");
    private static final List<String> COMPANY_SCOPES = List.of(
            "openid",
            "profile",
            "email",
            "w_member_social",
            "r_organization_admin",
            "w_organization_social");

    private final LinkedInCredentialService credentialService;
    private final LinkedInOAuthStateStore stateStore;
    private final LinkedInClient linkedInClient;
    private final LinkedInProperties properties;
    private final LinkedInExchangeStore exchangeStore;
    private final IntegrationService integrationService;
    private final CurrentUserProvider currentUserProvider;

    public LinkedInOAuthService(
            LinkedInCredentialService credentialService,
            LinkedInOAuthStateStore stateStore,
            LinkedInClient linkedInClient,
            LinkedInProperties properties,
            LinkedInExchangeStore exchangeStore,
            IntegrationService integrationService,
            CurrentUserProvider currentUserProvider) {
        this.credentialService = credentialService;
        this.stateStore = stateStore;
        this.linkedInClient = linkedInClient;
        this.properties = properties;
        this.exchangeStore = exchangeStore;
        this.integrationService = integrationService;
        this.currentUserProvider = currentUserProvider;
    }

    public AuthorizationUrlResponse authorizationUrl(String redirectUri, Long configId, String connectionType) {
        LinkedInAppCredentials credentials = credentialService.resolve(configId);
        String resolvedConnectionType = resolveConnectionType(connectionType);
        String effectiveRedirectUri = credentials.redirectUri() != null && !credentials.redirectUri().isBlank()
                ? credentials.redirectUri()
                : redirectUri;
        validateRedirectUri(effectiveRedirectUri);
        StateEntry entry = stateStore.create(
                currentUserProvider.currentUser(),
                credentials.configId(),
                effectiveRedirectUri,
                resolvedConnectionType);
        String scopes = effectiveScopes(resolvedConnectionType);
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

    public ExchangeResponse connectWithAuthorizationCode(String code, String state) {
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
        Instant expiresAt = token.expiresIn() != null && token.expiresIn() > 0
                ? Instant.now().plusSeconds(token.expiresIn())
                : null;
        List<LinkedInAccountToken> accounts = new ArrayList<>();
        if (CONNECTION_COMPANY.equals(entry.connectionType())) {
            linkedInClient.getAdminOrganizations(token.accessToken(), credentials.apiVersion()).stream()
                    .map(org -> new LinkedInAccountToken(org.id(), org.name(), CONNECTION_COMPANY))
                    .forEach(accounts::add);
            if (accounts.isEmpty()) {
                throw new BusinessException(
                        "No LinkedIn company Pages were found. Make sure this LinkedIn user is an approved Page admin and the app has organization permissions.");
            }
        } else {
            accounts.add(new LinkedInAccountToken(
                    authorUrn,
                    displayName(profile, profile.sub()),
                    CONNECTION_PERSONAL));
        }
        String exchangeId = exchangeStore.put(
                currentUserProvider.currentUser().userId(),
                credentials.configId(),
                token.accessToken(),
                token.tokenType() == null || token.tokenType().isBlank() ? TOKEN_TYPE : token.tokenType(),
                expiresAt,
                accounts);
        return new ExchangeResponse(
                exchangeId,
                accounts.stream()
                        .map(account -> new LinkedInAccountOption(
                                account.accountId(), account.displayName(), account.accountType()))
                        .toList(),
                expiresAt);
    }

    public List<IntegrationResponse> connectMany(String exchangeId, List<String> accountIds) {
        Exchange exchange = exchangeStore.get(exchangeId, currentUserProvider.currentUser().userId());
        List<String> requested = accountIds == null
                ? List.of()
                : accountIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
        if (requested.isEmpty()) {
            throw new BusinessException("Select at least one LinkedIn account to connect.");
        }
        return requested.stream()
                .map(accountId -> exchange.accounts().stream()
                        .filter(account -> account.accountId().equals(accountId))
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(
                                "Your LinkedIn selection expired or this account was not returned by LinkedIn.")))
                .map(account -> integrationService.persistConnection(
                        SocialPlatform.LINKEDIN,
                        new ProviderAccount(
                                SocialPlatform.LINKEDIN,
                                account.accountId(),
                                account.displayName(),
                                exchange.accessToken()),
                        exchange.tokenType(),
                        exchange.tokenExpiresAt(),
                        exchange.configId()))
                .toList();
    }

    private String effectiveScopes(String connectionType) {
        Set<String> scopes = new LinkedHashSet<>(
                CONNECTION_COMPANY.equals(connectionType) ? COMPANY_SCOPES : PERSONAL_SCOPES);
        return String.join(" ", scopes);
    }

    private String resolveConnectionType(String connectionType) {
        if (connectionType == null || connectionType.isBlank()) {
            return CONNECTION_PERSONAL;
        }
        String normalized = connectionType.trim().toUpperCase();
        if (CONNECTION_PERSONAL.equals(normalized) || CONNECTION_COMPANY.equals(normalized)) {
            return normalized;
        }
        throw new BusinessException("Unsupported LinkedIn connection type: " + connectionType);
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
