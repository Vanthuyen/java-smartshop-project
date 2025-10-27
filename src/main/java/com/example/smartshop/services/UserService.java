package com.example.smartshop.services;

import com.example.smartshop.models.dtos.requets.RegisterRequest;
import com.example.smartshop.models.dtos.responses.UserResponse;

public interface UserService {
    UserResponse createUser(RegisterRequest user);
}
