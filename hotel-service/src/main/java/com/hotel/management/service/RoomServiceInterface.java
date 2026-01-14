package com.hotel.management.service;

import com.hotel.management.dto.ConfirmAvailabilityRequest;
import com.hotel.management.dto.CreateRoomRequest;
import com.hotel.management.dto.RoomDto;
import com.hotel.management.dto.RoomStatisticsDto;

import java.time.LocalDate;
import java.util.List;

public interface RoomServiceInterface {

    List<RoomDto> getAllRooms();

    List<RoomDto> getAvailableRooms(LocalDate startDate, LocalDate endDate);

    List<RoomDto> getRecommendedRooms(LocalDate startDate, LocalDate endDate);

    RoomDto getRoomById(Long id);

    RoomDto createRoom(CreateRoomRequest request);

    boolean confirmAvailability(Long roomId, ConfirmAvailabilityRequest request);

    void releaseSlot(String requestId);

    List<RoomStatisticsDto> getRoomStatistics(Long hotelId, String sortBy, String sortDir);
}
