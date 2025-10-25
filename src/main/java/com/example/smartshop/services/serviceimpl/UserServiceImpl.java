package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.entities.UserEntity;
import com.example.smartshop.models.dtos.requets.LoginDTO;
import com.example.smartshop.models.dtos.responses.TokenDTO;
import com.example.smartshop.repositories.UserRepository;
import com.example.smartshop.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;


    @Override
    public UserEntity createUser(UserEntity user) {
        return null;
    }

}
