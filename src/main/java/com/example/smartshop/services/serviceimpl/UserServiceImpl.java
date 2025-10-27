package com.example.smartshop.services.serviceimpl;

import com.example.smartshop.commons.enums.Role;
import com.example.smartshop.entities.UserEntity;
import com.example.smartshop.models.dtos.requets.LoginDTO;
import com.example.smartshop.models.dtos.requets.RegisterRequest;
import com.example.smartshop.models.dtos.responses.TokenDTO;
import com.example.smartshop.models.dtos.responses.UserResponse;
import com.example.smartshop.models.mappers.UserMapper;
import com.example.smartshop.repositories.UserRepository;
import com.example.smartshop.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserMapper userMapper;


    @Override
    public UserResponse createUser(RegisterRequest request) {
        if (userRepository.findByEmailAndDeletedAtIsNull(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already exists!");
        }
        UserEntity user = userMapper.toUserEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.CUSTOMER);
        UserEntity savedUser = userRepository.save(user);
        return userMapper.toResponse(savedUser);
    }

}
