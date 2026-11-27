package com.baolong.wsw.security;

import com.baolong.wsw.config.WswProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。
 *
 * <p><b>密钥从哪来</b>（这件事本身是安全问题，不是配置细节）：
 * <ol>
 *   <li>优先读配置 {@code wsw.jwt.secret}，也就是环境变量 {@code WSW_JWT_SECRET}；</li>
 *   <li>没有配置时，用 {@link SecureRandom} 生成 64 位十六进制密钥，落盘到
 *       {@code uploads/.jwt-secret}（该目录已在 .gitignore，不会进仓库）；</li>
 *   <li>落盘的意义：服务重启后旧 token 仍然有效，不会把所有人踢下线。</li>
 * </ol>
 * 绝对不能把密钥硬编码在仓库里 —— 本仓库是公开的，拿到源码就能伪造管理员 token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtUtil {

    /**
     * 密钥文件位置：<b>不能放在 uploads/ 下</b>。
     * 因为 {@code /files/upload/**} 是静态直出的资源目录，放在那里等于把签名密钥挂到公网上，
     * 谁都能下载到然后伪造 token —— 比硬编码好不了多少。
     * 这里选工作目录下的 {@code .wsw/}，并已加入 .gitignore。
     */
    private static final String SECRET_FILE = ".wsw/jwt-secret";

    private final WswProperties properties;

    @PostConstruct
    void initSecret() {
        String configured = properties.getJwt().getSecret();
        if (configured != null && !configured.isBlank()) {
            return;
        }
        String generated = readOrCreateLocalSecret();
        properties.getJwt().setSecret(generated);
        log.warn("未配置 WSW_JWT_SECRET，已使用本地随机密钥（{}）。"
                + "自己开发够用；部署到服务器请设置环境变量 WSW_JWT_SECRET。", SECRET_FILE);
    }

    private String readOrCreateLocalSecret() {
        Path path = Paths.get(SECRET_FILE);
        try {
            if (Files.exists(path)) {
                String existing = Files.readString(path, StandardCharsets.UTF_8).trim();
                if (existing.length() >= 32) {
                    return existing;
                }
            }
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            String secret = sb.toString();
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, secret, StandardCharsets.UTF_8);
            return secret;
        } catch (IOException e) {
            // 连密钥都存不下来时，宁可启动失败也不要退化成一个公开的默认值
            throw new IllegalStateException("JWT 密钥不可用：请设置环境变量 WSW_JWT_SECRET", e);
        }
    }

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