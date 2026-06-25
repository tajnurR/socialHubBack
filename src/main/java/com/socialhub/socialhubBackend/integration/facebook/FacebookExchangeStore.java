package com.socialhub.socialhubBackend.integration.facebook;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Short-lived, in-memory store for resolved Page tokens between the OAuth
 * {@code exchange} step (which returns selectable pages) and the {@code connect}
 * step (which persists the chosen page). Page tokens are held here only — they
 * are never returned to the frontend or logged.
 *
 * <p>TTL-bounded. Note: in-memory, so single-instance only; move to a shared
 * store (e.g. Redis with encryption) if running multiple backend instances.
 */
@Component
public class FacebookExchangeStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    /** A page the authorizing user manages, with its (sensitive) Page access token. */
    public record PageToken(String pageId, String name, String accessToken) {}

    private record Entry(Instant expiresAt, List<PageToken> pages) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** Stores the resolved pages and returns an opaque exchange id. */
    public String put(List<PageToken> pages) {
        evictExpired();
        String exchangeId = UUID.randomUUID().toString();
        store.put(exchangeId, new Entry(Instant.now().plus(TTL), List.copyOf(pages)));
        return exchangeId;
    }

    public Optional<PageToken> resolve(String exchangeId, String pageId) {
        Entry entry = current(exchangeId);
        if (entry == null) {
            return Optional.empty();
        }
        return entry.pages().stream().filter(p -> p.pageId().equals(pageId)).findFirst();
    }

    private Entry current(String exchangeId) {
        if (exchangeId == null) {
            return null;
        }
        Entry entry = store.get(exchangeId);
        if (entry == null) {
            return null;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(exchangeId);
            return null;
        }
        return entry;
    }

    private void evictExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
