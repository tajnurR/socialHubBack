package com.socialhub.socialhubBackend.common.security;

import com.socialhub.socialhubBackend.config.AppProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Symmetric encryption for secrets at rest (e.g. integration access tokens).
 *
 * <p>AES-256-GCM. The key is derived (SHA-256) from {@code app.crypto.secret} so
 * any passphrase works; supply a strong, stable secret per environment via the
 * {@code APP_ENCRYPTION_SECRET} env var. Output is Base64 of {@code IV || ciphertext}.
 *
 * <p>Note: rotating the secret invalidates previously stored ciphertext — a key
 * rotation/re-encryption routine is out of scope for this foundation (TODO).
 */
@Service
public class EncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EncryptionService(AppProperties properties) {
        this.key = deriveKey(properties.crypto().secret());
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array();
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt value", ex);
        }
    }

    public String decrypt(String stored) {
        try {
            byte[] combined = Base64.getDecoder().decode(stored);
            ByteBuffer buffer = ByteBuffer.wrap(combined);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decrypt value", ex);
        }
    }

    private SecretKeySpec deriveKey(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(hash, "AES");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to derive encryption key", ex);
        }
    }
}
