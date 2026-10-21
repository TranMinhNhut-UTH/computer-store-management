package com.computerstore.security;

import com.computerstore.config.JwtProperties;
import com.computerstore.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public record AccessToken(String token, Instant expiresAt) {}

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        if (props.key() == null || props.key().length() < 32) {
            throw new IllegalStateException(
                    "Thiếu app.jwt.key (tối thiểu 32 ký tự). Đặt biến môi trường JWT_KEY "
                            + "hoặc khai báo trong application-local.yml.");
        }
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.key().getBytes(StandardCharsets.UTF_8));
    }

    public AccessToken createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expires = now.plus(Duration.ofMinutes(props.accessTokenMinutes()));
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(props.issuer())
                .audience().add(props.audience()).and()
                .subject(String.valueOf(user.getId()))
                .claim("role", user.getRole().getCode())
                .claim("name", user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new AccessToken(token, expires);
    }

    /** Ném JwtException nếu token sai chữ ký, hết hạn, hoặc sai issuer/audience. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .requireAudience(props.audience())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
