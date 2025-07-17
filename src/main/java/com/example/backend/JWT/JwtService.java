package com.example.backend.JWT;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function; // ДОБАВЛЕНО: Импорт Optional
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import com.example.backend.entiity.User; // ДОБАВЛЕНО: Импорт вашей сущности User

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    @Value("${jwt.secret:}")
    private String SECRET_KEY_STRING;

    @Value("${jwt.expiration:3600000}") 
    private long JWT_EXPIRATION_MS;

    private Key cachedSignInKey;

    // В JwtService.java
private Key getSignInKey() {
    if (cachedSignInKey == null) {
        if (SECRET_KEY_STRING == null || SECRET_KEY_STRING.length() < 32) {
            cachedSignInKey = Keys.secretKeyFor(SignatureAlgorithm.HS256); // Генерируем новый ключ
            // ВАЖНО: В продакшене этот ключ должен быть стабильным и не генерироваться каждый раз.
            // Возможно, стоит вывести его в логи один раз...
        } else {
            cachedSignInKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_KEY_STRING));
        }
    }
    return cachedSignInKey;
}

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", userDetails.getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.toList()));
        return generateToken(claims, userDetails);
    }

    public String generateToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails
    ) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ДОБАВЛЕНО: Метод для генерации токена имперсонации
    public String generateImpersonationToken(UserDetails impersonatedUser, Long adminId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("roles", impersonatedUser.getAuthorities().stream()
                                .map(GrantedAuthority::getAuthority)
                                .collect(Collectors.toList()));
        claims.put("isImpersonating", true);
        claims.put("impersonatedUserId", ((User) impersonatedUser).getId());
        claims.put("adminId", adminId);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(impersonatedUser.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_EXPIRATION_MS))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    // ДОБАВЛЕНО: Метод для извлечения ID имперсонируемого пользователя
    public Optional<Long> extractImpersonatedUserId(String token) {
        Claims claims = extractAllClaims(token);
        if (claims.containsKey("isImpersonating") && (Boolean) claims.get("isImpersonating")) {
            // Убедитесь, что "impersonatedUserId" действительно Long или Number
            Object userIdObject = claims.get("impersonatedUserId");
            if (userIdObject instanceof Number) {
                return Optional.of(((Number) userIdObject).longValue());
            }
        }
        return Optional.empty();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }
}