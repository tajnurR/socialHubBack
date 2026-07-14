package com.socialhub.socialhubBackend.integration.linkedin;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.user.context.CurrentUser;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LinkedInOAuthStateStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final SecureRandom random = new SecureRandom();
    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();

    public StateEntry create(CurrentUser user, Long configId, String redirectUri, String connectionType) {
        cleanup();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        StateEntry entry = new StateEntry(
                state,
                user.organizationId(),
                user.userId(),
                configId,
                redirectUri,
                connectionType,
                Instant.now().plus(TTL));
        states.put(state, entry);
        return entry;
    }

    public StateEntry consume(String state, CurrentUser user) {
        StateEntry entry = Optional.ofNullable(states.remove(state))
                .orElseThrow(() -> new BusinessException(
                        "LinkedIn authorization session expired. Try connecting again.",
                        HttpStatus.BAD_REQUEST));
        if (entry.expiresAt().isBefore(Instant.now())
                || !entry.organizationId().equals(user.organizationId())
                || !entry.userId().equals(user.userId())) {
            throw new BusinessException(
                    "LinkedIn authorization session expired. Try connecting again.",
                    HttpStatus.BAD_REQUEST);
        }
        return entry;
    }

    private void cleanup() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, StateEntry>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().expiresAt().isBefore(now)) {
                iterator.remove();
            }
        }
    }

    public record StateEntry(
            String state,
            Long organizationId,
            Long userId,
            Long configId,
            String redirectUri,
            String connectionType,
            Instant expiresAt) {}
}
