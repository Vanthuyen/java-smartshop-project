package com.example.smartshop.controllers;

import com.example.smartshop.commons.utils.ResponseUtil;
import com.example.smartshop.models.dtos.requets.LoginDTO;
import com.example.smartshop.models.dtos.requets.RegisterRequest;
import com.example.smartshop.models.dtos.responses.ApiResponse;
import com.example.smartshop.models.dtos.responses.TokenDTO;
import com.example.smartshop.models.dtos.responses.UserResponse;
import com.example.smartshop.services.AuthService;
import com.example.smartshop.services.RedisService;
import com.example.smartshop.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth Management", description = "APIs for managing authentication")
public class AuthController {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @Autowired
    private RedisService redisService;

    @PostMapping("/login")
    @Operation(summary = "Login")
    public ResponseEntity<ApiResponse<TokenDTO>> login(@Valid @RequestBody LoginDTO loginDTO) {
        TokenDTO token = authService.login(loginDTO);
        return ResponseUtil.success("Login successfully",token);
    }

    @PostMapping("/register")
    @Operation(summary = "Register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest register) {
        UserResponse user = userService.createUser(register);
        return ResponseUtil.success("Register successfully",user);
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout")
    public ResponseEntity<ApiResponse<Object>> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);

            long expirationMillis = authService.getExpirationMillis(token);
            redisService.addToBlacklist(token, expirationMillis);

            SecurityContextHolder.clearContext();

            return ResponseUtil.success("Logged out successfully", null);
        }

        return ResponseUtil.error("No valid token found", HttpStatus.UNAUTHORIZED);
    }
}
