package com.example.smartshop.models.mappers;

import com.example.smartshop.entities.UserEntity;
import com.example.smartshop.models.dtos.requets.RegisterRequest;
import com.example.smartshop.models.dtos.responses.UserResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserResponse toResponse(UserEntity user);
    UserEntity toUserEntity(RegisterRequest registerRequest);
}
