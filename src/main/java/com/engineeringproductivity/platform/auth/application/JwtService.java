package com.engineeringproductivity.platform.auth.application;

import com.engineeringproductivity.platform.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

/**
 * Handles all JWT operations: generation, parsing, and validation.
 *
 * Token structure:
 *   sub      → user email
 *   userId   → user UUID (string)
 *   role     → e.g. "DEVELOPER"
 *   type     → "access" | "refresh"
 *   iat      → issued at (epoch seconds)
 *   exp      → expiry (epoch seconds)
 */
@Service
public class JwtService {

    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        this.props = props;
    }

    // ── Token generation ──────────────────────────────────────────────────────

    public String generateAccessToken(User user) {
        return build(user, props.accessTokenExpiration(), "access");
    }

    public String generateRefreshToken(User user) {
        return build(user, props.refreshTokenExpiration(), "refresh");
    }

    private String build(User user, long expirationMs, String type) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim("userId", user.getId().toString())
                .claim("role", user.getRole().name())
                .claim("type", type)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // ── Token parsing ─────────────────────────────────────────────────────────

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public UUID extractUserId(String token) {
        return UUID.fromString((String) extractAllClaims(token).get("userId"));
    }

    public String extractRole(String token) {
        return (String) extractAllClaims(token).get("role");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(props.secret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
