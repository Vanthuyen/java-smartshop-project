package com.example.smartshop.services;

import com.example.smartshop.models.dtos.requets.LoginDTO;
import com.example.smartshop.models.dtos.responses.TokenDTO;

public interface AuthService {
    TokenDTO login(LoginDTO login);
}
