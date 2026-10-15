package com.wordonline.server.bot.llm;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * The provider API key format shared with the admin server.
 *
 * <p>Key = SHA-256 of the UTF-8 {@code LLM_KEY_SECRET}; cipher = AES/GCM/NoPadding with a 12-byte
 * IV and a 128-bit tag; AAD = the UTF-8 provider name, so a ciphertext cannot be moved to another
 * provider's row; stored string = {@code "v1:"} + standard padded Base64 of IV || ciphertext+tag.
 *
 * <p>Exceptions thrown here carry neither the key nor the secret.
 */
@Component
public class LlmKeyCipher {

    static final String PREFIX = "v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String secret;

    public LlmKeyCipher(LlmBotProperties properties) {
        this(properties.keySecret());
    }

    LlmKeyCipher(String secret) {
        this.secret = secret == null ? "" : secret;
    }

    public boolean isConfigured() {
        return !secret.isBlank();
    }

    public String decrypt(String provider, String stored) {
        requireConfigured();
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new LlmKeyCipherException("Stored key for " + provider + " is not in the v1 format.");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new LlmKeyCipherException("Stored key for " + provider + " is not valid Base64.");
        }
        if (payload.length <= IV_LENGTH) {
            throw new LlmKeyCipherException("Stored key for " + provider + " is too short.");
        }
        try {
            Cipher cipher = cipher(Cipher.DECRYPT_MODE, provider, payload, 0);
            byte[] plain = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // The cause is dropped on purpose: it says nothing useful and must not travel with key material.
            throw new LlmKeyCipherException("Stored key for " + provider
                    + " could not be decrypted; LLM_KEY_SECRET may differ from the admin server's.");
        }
    }

    public String encrypt(String provider, String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        return encrypt(provider, plaintext, iv);
    }

    String encrypt(String provider, String plaintext, byte[] iv) {
        requireConfigured();
        if (iv.length != IV_LENGTH) {
            throw new IllegalArgumentException("IV must be " + IV_LENGTH + " bytes.");
        }
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, provider, iv, 0);
            byte[] sealed = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[IV_LENGTH + sealed.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH);
            System.arraycopy(sealed, 0, payload, IV_LENGTH, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new LlmKeyCipherException("Key for " + provider + " could not be encrypted.");
        }
    }

    private Cipher cipher(int mode, String provider, byte[] ivSource, int ivOffset) throws GeneralSecurityException {
        byte[] key = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, ivSource, ivOffset, IV_LENGTH));
        cipher.updateAAD(provider.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new LlmKeyCipherException("LLM_KEY_SECRET is blank.");
        }
    }

    public static class LlmKeyCipherException extends RuntimeException {
        public LlmKeyCipherException(String message) {
            super(message);
        }
    }
}
