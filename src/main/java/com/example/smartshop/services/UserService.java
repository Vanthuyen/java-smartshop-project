package com.example.smartshop.services;

import com.example.smartshop.entities.UserEntity;
import com.example.smartshop.models.dtos.requets.LoginDTO;
import com.example.smartshop.models.dtos.responses.TokenDTO;

public interface UserService {
    UserEntity createUser(UserEntity user);
}
