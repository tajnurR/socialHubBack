package com.socialhub.socialhubBackend.auth.jwt;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.socialhub.socialhubBackend.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * HMAC (HS256) JWT signing key + encoder/decoder. The key is derived (SHA-256)
 * from {@code app.auth.jwt-secret} so any passphrase yields a valid 256-bit key.
 *
 * <p>This is the symmetric variant for first-party login. For SSO/OIDC, replace
 * the decoder with one pointing at the IdP's JWK set (issuer-uri) — the rest of
 * the security chain is unchanged.
 */
@Configuration
public class JwtConfig {

    @Bean
    public SecretKey jwtSecretKey(AuthProperties authProperties) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(authProperties.jwtSecret().getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "HmacSHA256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to derive JWT signing key", ex);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecretKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey jwtSecretKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
