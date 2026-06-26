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
 * Short-lived, in-memory store bridging the OAuth {@code exchange} step (which
 * returns selectable pages) and the {@code connect} step (which persists the
 * chosen page). Holds the resolved Page tokens plus the app config used
 * (configId + version) so connect can link the connection and call Graph with the
 * right version. Page tokens are held here only — never returned to the frontend
 * or logged.
 *
 * <p>TTL-bounded. In-memory, so single-instance only; move to a shared encrypted
 * store (e.g. Redis) if running multiple backend instances.
 */
@Component
public class FacebookExchangeStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    /** A page the authorizing user manages, with its (sensitive) Page access token. */
    public record PageToken(String pageId, String name, String accessToken) {}

    /** The app config used for an exchange (to link the connection + pick the Graph version). */
    public record ExchangeMeta(Long configId, String apiVersion) {}

    private record Entry(Instant expiresAt, Long configId, String apiVersion, List<PageToken> pages) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    /** Stores the resolved pages + the app config used; returns an opaque exchange id. */
    public String put(Long configId, String apiVersion, List<PageToken> pages) {
        evictExpired();
        String exchangeId = UUID.randomUUID().toString();
        store.put(exchangeId, new Entry(Instant.now().plus(TTL), configId, apiVersion, List.copyOf(pages)));
        return exchangeId;
    }

    public Optional<PageToken> resolve(String exchangeId, String pageId) {
        Entry entry = current(exchangeId);
        if (entry == null) {
            return Optional.empty();
        }
        return entry.pages().stream().filter(p -> p.pageId().equals(pageId)).findFirst();
    }

    public Optional<ExchangeMeta> meta(String exchangeId) {
        Entry entry = current(exchangeId);
        return entry == null
                ? Optional.empty()
                : Optional.of(new ExchangeMeta(entry.configId(), entry.apiVersion()));
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
