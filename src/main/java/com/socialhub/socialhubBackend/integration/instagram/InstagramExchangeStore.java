package com.socialhub.socialhubBackend.integration.instagram;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InstagramExchangeStore {

    private static final Duration TTL = Duration.ofMinutes(10);
    private final Map<String, Entry> exchanges = new ConcurrentHashMap<>();

    public String put(Long userId, Long configId, String apiVersion, List<InstagramAccountToken> accounts) {
        cleanup();
        String id = UUID.randomUUID().toString();
        exchanges.put(id, new Entry(userId, configId, apiVersion, accounts, Instant.now().plus(TTL)));
        return id;
    }

    public Optional<ExchangeMeta> meta(String exchangeId) {
        Entry entry = exchanges.get(exchangeId);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            exchanges.remove(exchangeId);
            return Optional.empty();
        }
        return Optional.of(new ExchangeMeta(entry.userId(), entry.configId(), entry.apiVersion()));
    }

    public Optional<InstagramAccountToken> resolve(String exchangeId, String instagramAccountId) {
        Entry entry = exchanges.get(exchangeId);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            exchanges.remove(exchangeId);
            return Optional.empty();
        }
        return entry.accounts().stream()
                .filter(account -> account.instagramAccountId().equals(instagramAccountId))
                .findFirst();
    }

    private void cleanup() {
        Instant now = Instant.now();
        exchanges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Entry(
            Long userId,
            Long configId,
            String apiVersion,
            List<InstagramAccountToken> accounts,
            Instant expiresAt) {}

    public record ExchangeMeta(Long userId, Long configId, String apiVersion) {}

    public record InstagramAccountToken(
            String instagramAccountId,
            String displayName,
            String pageId,
            String pageName,
            String pageAccessToken) {}
}
