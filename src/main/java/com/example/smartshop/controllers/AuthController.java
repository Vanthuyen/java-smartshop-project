package com.example.smartshop.controllers;

import com.example.smartshop.commons.utils.ResponseUtil;
import com.example.smartshop.models.dtos.requets.LoginDTO;
import com.example.smartshop.models.dtos.requets.RegisterRequest;
import com.example.smartshop.models.dtos.responses.ApiResponse;
import com.example.smartshop.models.dtos.responses.TokenDTO;
import com.example.smartshop.models.dtos.responses.UserResponse;
import com.example.smartshop.services.AuthService;
import com.example.smartshop.services.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    @Autowired
    private AuthService authService;

    @Autowired
    private UserService userService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenDTO>> login(@Valid @RequestBody LoginDTO loginDTO) {
        TokenDTO token = authService.login(loginDTO);
        return ResponseUtil.success("Login successfully",token);
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest register) {
        UserResponse user = userService.createUser(register);
        return ResponseUtil.success("Register successfully",user);
    }
}
