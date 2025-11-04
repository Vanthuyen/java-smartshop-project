package com.example.smartshop.services;

import com.example.smartshop.entities.UserEntity;
import io.jsonwebtoken.Claims;
import org.springframework.security.core.userdetails.UserDetails;

public interface JWTService {
    String generateTokenWithUserInfo(UserEntity user);
    String extractEmail(String token);
    boolean isTokenExpired(String token);
    boolean isTokenValid(String token, UserDetails userDetails);
    Claims extractAllClaims(String token);
    long getExpirationMillis(String token);
}
