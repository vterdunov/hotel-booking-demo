package com.hotel.management.service;

import com.hotel.management.dto.CreateHotelRequest;
import com.hotel.management.dto.HotelDto;

import java.util.List;

public interface HotelServiceInterface {

    List<HotelDto> getAllHotels();

    HotelDto getHotelById(Long id);

    HotelDto createHotel(CreateHotelRequest request);

    void deleteHotel(Long id);
}
