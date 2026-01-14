package com.hotel.booking.service;

import com.hotel.booking.dto.AuthRequest;
import com.hotel.booking.dto.AuthResponse;
import com.hotel.booking.dto.CreateUserRequest;
import com.hotel.booking.dto.RegisterRequest;
import com.hotel.booking.dto.UpdateUserRequest;
import com.hotel.booking.dto.UserDto;

import java.util.List;

public interface UserServiceInterface {

    AuthResponse register(RegisterRequest request);

    AuthResponse authenticate(AuthRequest request);

    UserDto createUser(CreateUserRequest request);

    UserDto updateUser(Long id, UpdateUserRequest request);

    void deleteUser(Long id);

    List<UserDto> getAllUsers();

    UserDto getUserById(Long id);
}
