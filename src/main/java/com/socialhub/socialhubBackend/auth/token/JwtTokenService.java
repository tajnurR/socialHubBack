package com.socialhub.socialhubBackend.auth.token;

import com.socialhub.socialhubBackend.common.exception.BusinessException;
import com.socialhub.socialhubBackend.config.AuthProperties;
import com.socialhub.socialhubBackend.user.domain.User;
import com.socialhub.socialhubBackend.user.domain.UserStatus;
import com.socialhub.socialhubBackend.user.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

/**
 * JWT (HS256) implementation of {@link TokenService}. Access tokens carry the
 * user id (subject), email, organization, and roles so the security context can
 * be rebuilt without a DB lookup. Refresh tokens are marked {@code type=refresh}.
 */
@Service
public class JwtTokenService implements TokenService {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_ORG = "org";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_EMAIL = "email";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AuthProperties authProperties;
    private final UserRepository userRepository;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            JwtDecoder jwtDecoder,
            AuthProperties authProperties,
            UserRepository userRepository) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.authProperties = authProperties;
        this.userRepository = userRepository;
    }

    @Override
    public AuthTokens issue(User user) {
        Instant now = Instant.now();
        String access = encode(user, now, authProperties.accessTokenTtl().getSeconds(), TYPE_ACCESS);
        String refresh = encode(user, now, authProperties.refreshTokenTtl().getSeconds(), TYPE_REFRESH);
        return new AuthTokens(access, refresh, authProperties.accessTokenTtl().getSeconds());
    }

    @Override
    public AuthTokens refresh(String refreshToken) {
        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(refreshToken);
        } catch (JwtException ex) {
            throw new BusinessException("Invalid or expired refresh token", HttpStatus.UNAUTHORIZED);
        }
        if (!TYPE_REFRESH.equals(jwt.getClaimAsString(CLAIM_TYPE))) {
            throw new BusinessException("Not a refresh token", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository
                .findById(Long.valueOf(jwt.getSubject()))
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException("User no longer active", HttpStatus.UNAUTHORIZED));
        return issue(user);
    }

    private String encode(User user, Instant issuedAt, long ttlSeconds, String type) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(ttlSeconds, ChronoUnit.SECONDS))
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ORG, user.getOrganizationId())
                .claim(CLAIM_ROLES, List.of(user.getRole().name()))
                .build();
        return jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
