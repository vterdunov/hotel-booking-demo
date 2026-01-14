package com.hotel.management.mapper;

import com.hotel.management.dto.RoomDto;
import com.hotel.management.entity.Room;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface RoomMapper {

    @Mapping(source = "hotel.id", target = "hotelId")
    @Mapping(source = "hotel.name", target = "hotelName")
    RoomDto toDto(Room room);

    List<RoomDto> toDtoList(List<Room> rooms);
}
