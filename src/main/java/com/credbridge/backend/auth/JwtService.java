package com.credbridge.backend.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final byte[] secret;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-minutes}") long expirationMinutes
    ) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationMinutes * 60;
    }

    public String generateToken(User user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + expirationSeconds;

        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("""
                {"sub":"%s","role":"%s","iat":%d,"exp":%d}
                """.formatted(user.getEmail(), user.getRole().name(), issuedAt, expiresAt).trim());
        String unsignedToken = header + "." + payload;

        return unsignedToken + "." + sign(unsignedToken);
    }

    public String extractSubject(String token) {
        String[] parts = splitToken(token);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        return extractStringClaim(payload, "sub");
    }

    public boolean isValid(String token) {
        String[] parts = splitToken(token);
        String unsignedToken = parts[0] + "." + parts[1];

        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            return false;
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        long expiresAt = extractLongClaim(payload, "exp");
        return Instant.now().getEpochSecond() < expiresAt;
    }

    private String[] splitToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new InvalidCredentialsException("Invalid token");
        }
        return parts;
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign token", exception);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String extractStringClaim(String json, String claimName) {
        String marker = "\"" + claimName + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new InvalidCredentialsException("Invalid token");
        }

        int valueStart = start + marker.length();
        int valueEnd = json.indexOf("\"", valueStart);
        if (valueEnd < 0) {
            throw new InvalidCredentialsException("Invalid token");
        }

        return json.substring(valueStart, valueEnd);
    }

    private long extractLongClaim(String json, String claimName) {
        String marker = "\"" + claimName + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new InvalidCredentialsException("Invalid token");
        }

        int valueStart = start + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < json.length() && Character.isDigit(json.charAt(valueEnd))) {
            valueEnd++;
        }

        return Long.parseLong(json.substring(valueStart, valueEnd));
    }

    private boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8)
        );
    }
}
