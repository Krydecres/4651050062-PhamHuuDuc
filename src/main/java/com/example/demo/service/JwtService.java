package com.example.demo.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private static final String SECRET_KEY = "c3ByaW5nYm9vdC1sYWItand0LXNlY3JldC0yMDI2LTMyISE=";

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(SECRET_KEY));
    }

    public String generateToken(String username) {
        return generateToken(null, username);
    }

    public String generateToken(Integer idUser, String username) {
        var builder = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60 * 60 * 1000));

        if (idUser != null) {
            builder.claim("idUser", idUser);
        }

        return builder.signWith(getKey()).compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    public Integer extractIdUser(String token) {
        Object idUserObj = extractAllClaims(token).get("idUser");
        if (idUserObj instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}