package com.hotel.booking.mapper;

import com.hotel.booking.dto.UserDto;
import com.hotel.booking.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserDto toDto(User user);

    List<UserDto> toDtoList(List<User> users);
}
