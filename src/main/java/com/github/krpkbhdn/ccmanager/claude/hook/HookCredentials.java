package com.github.krpkbhdn.ccmanager.claude.hook;

import com.sun.net.httpserver.HttpExchange;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Per-run secrets of the hook server: one HMAC key for the session credentials and a separate secret for the Assistant. */
final class HookCredentials {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final int SECRET_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret = secret();
    private final byte[] assistantSecret = secret();

    String sessionToken(String ccmSessionId) {
        return new String(credential(ccmSessionId), StandardCharsets.US_ASCII);
    }

    String assistantToken() {
        return new String(assistantSecret, StandardCharsets.US_ASCII);
    }

    boolean isAssistant(HttpExchange exchange) {
        return MessageDigest.isEqual(bearerOf(exchange).getBytes(StandardCharsets.UTF_8), assistantSecret);
    }

    /** The session id in the path of a request whose bearer token is that session's credential, otherwise empty. */
    Optional<String> authorizedSessionId(HttpExchange exchange, String contextPath) {
        var ccmSessionId = exchange.getRequestURI().getPath().substring(contextPath.length());
        return !ccmSessionId.isEmpty() && !ccmSessionId.contains("/") && credentialMatches(bearerOf(exchange), ccmSessionId)
                ? Optional.of(ccmSessionId) : Optional.empty();
    }

    private static String bearerOf(HttpExchange exchange) {
        var header = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization")).orElse("");
        return header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : "";
    }

    private boolean credentialMatches(String candidate, String ccmSessionId) {
        return MessageDigest.isEqual(candidate.getBytes(StandardCharsets.UTF_8), credential(ccmSessionId));
    }

    /** HMAC of the session id under the run secret, so a URL of one session never authorizes another. */
    private byte[] credential(String ccmSessionId) {
        try {
            var mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            var digest = mac.doFinal(ccmSessionId.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).getBytes(StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", e);
        }
    }

    private static byte[] secret() {
        var bytes = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).getBytes(StandardCharsets.US_ASCII);
    }
}
