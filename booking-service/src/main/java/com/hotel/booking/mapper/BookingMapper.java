package com.hotel.booking.mapper;

import com.hotel.booking.dto.BookingDto;
import com.hotel.booking.entity.Booking;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface BookingMapper {

    @Mapping(source = "user.id", target = "userId")
    BookingDto toDto(Booking booking);

    List<BookingDto> toDtoList(List<Booking> bookings);
}
