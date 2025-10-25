package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.entities.UserEntity;
import com.example.smartshop.models.dtos.requets.LoginDTO;
import com.example.smartshop.models.dtos.responses.TokenDTO;
import com.example.smartshop.repositories.UserRepository;
import com.example.smartshop.services.AuthService;
import com.example.smartshop.services.JWTService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JWTService jwtService;

    @Override
    public TokenDTO login(LoginDTO login) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(login.getEmail(), login.getPassword())
            );

            UserEntity user = userRepository.findByEmail(login.getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String token = jwtService.generateTokenWithUserInfo(user);

            return TokenDTO.builder()
                    .token(token)
                    .userId(user.getId())
                    .role(user.getRole().name())
                    .build();

        } catch (AuthenticationException e) {
            throw new RuntimeException("Invalid email or password");
        }
    }
}
