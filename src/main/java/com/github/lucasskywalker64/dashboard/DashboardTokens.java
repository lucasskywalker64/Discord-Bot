package com.github.lucasskywalker64.dashboard;

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

final class DashboardTokens {
    private static final int TOKEN_BYTES = 32;
    private static final int ENCODED_LENGTH = 43; // Unpadded Base64 length for TOKEN_BYTES.
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern TOKEN = Pattern.compile("[a-zA-Z0-9_-]{" + ENCODED_LENGTH + "}");

    private DashboardTokens() {
    }

    static String create() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static boolean valid(@Nullable String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    static String hash(String token) {
        return HexFormat.of().formatHex(digest(token));
    }

    static String challenge(String verifier) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier));
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
