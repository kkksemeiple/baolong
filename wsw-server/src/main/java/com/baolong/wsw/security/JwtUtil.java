package com.baolong.wsw.security;

import com.baolong.wsw.config.WswProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。
 */
@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final WswProperties properties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 token */
    public String create(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.getJwt().getExpireSeconds());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key())
                .compact();
    }

    /** 解析 token，非法或过期返回 null */
    public LoginUser parse(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    claims.get("role", String.class));
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
