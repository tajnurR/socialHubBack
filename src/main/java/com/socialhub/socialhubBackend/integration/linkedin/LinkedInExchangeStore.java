package com.socialhub.socialhubBackend.integration.linkedin;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LinkedInExchangeStore {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Exchange> exchanges = new ConcurrentHashMap<>();

    public String put(Long userId, Long configId, String accessToken, String tokenType, Instant tokenExpiresAt,
            List<LinkedInAccountToken> accounts) {
        cleanup();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String exchangeId = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        exchanges.put(exchangeId, new Exchange(
                userId,
                configId,
                accessToken,
                tokenType,
                tokenExpiresAt,
                Instant.now().plus(TTL),
                accounts));
        return exchangeId;
    }

    public Exchange get(String exchangeId, Long userId) {
        cleanup();
        Exchange exchange = Optional.ofNullable(exchanges.get(exchangeId))
                .orElseThrow(() -> new BusinessException("Your LinkedIn session expired. Please connect again."));
        if (exchange.expiresAt().isBefore(Instant.now()) || !exchange.userId().equals(userId)) {
            exchanges.remove(exchangeId);
            throw new BusinessException("Your LinkedIn session expired. Please connect again.");
        }
        return exchange;
    }

    private void cleanup() {
        Instant now = Instant.now();
        exchanges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    public record Exchange(
            Long userId,
            Long configId,
            String accessToken,
            String tokenType,
            Instant tokenExpiresAt,
            Instant expiresAt,
            List<LinkedInAccountToken> accounts) {}

    public record LinkedInAccountToken(String accountId, String displayName, String accountType) {}
}
