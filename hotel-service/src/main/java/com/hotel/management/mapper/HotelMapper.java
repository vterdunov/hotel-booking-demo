package com.hotel.management.mapper;

import com.hotel.management.dto.CreateHotelRequest;
import com.hotel.management.dto.HotelDto;
import com.hotel.management.entity.Hotel;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface HotelMapper {

    HotelDto toDto(Hotel hotel);

    List<HotelDto> toDtoList(List<Hotel> hotels);

    Hotel toEntity(CreateHotelRequest request);
}
