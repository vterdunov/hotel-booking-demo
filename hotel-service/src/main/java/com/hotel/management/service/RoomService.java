package com.hotel.management.service;

import com.hotel.management.dto.ConfirmAvailabilityRequest;
import com.hotel.management.dto.CreateRoomRequest;
import com.hotel.management.dto.RoomDto;
import com.hotel.management.dto.RoomStatisticsDto;
import com.hotel.management.entity.Hotel;
import com.hotel.management.entity.Room;
import com.hotel.management.entity.RoomSlot;
import com.hotel.management.mapper.RoomMapper;
import com.hotel.management.repository.HotelRepository;
import com.hotel.management.repository.RoomRepository;
import com.hotel.management.repository.RoomSlotRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomService implements RoomServiceInterface {

    private final RoomRepository roomRepository;
    private final RoomSlotRepository roomSlotRepository;
    private final HotelRepository hotelRepository;
    private final RoomMapper roomMapper;

    @Override
    @Transactional(readOnly = true)
    public List<RoomDto> getAllRooms() {
        return roomMapper.toDtoList(roomRepository.findAll());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomDto> getAvailableRooms(LocalDate startDate, LocalDate endDate) {
        return roomMapper.toDtoList(roomRepository.findAvailableRooms(startDate, endDate));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomDto> getRecommendedRooms(LocalDate startDate, LocalDate endDate) {
        return roomMapper.toDtoList(roomRepository.findRecommendedRooms(startDate, endDate));
    }

    @Override
    @Transactional(readOnly = true)
    public RoomDto getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Room not found with id: " + id));
        return roomMapper.toDto(room);
    }

    @Override
    @Transactional
    public RoomDto createRoom(CreateRoomRequest request) {
        Hotel hotel = hotelRepository.findById(request.getHotelId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Hotel not found with id: " + request.getHotelId()));

        Room room = Room.builder()
                .hotel(hotel)
                .number(request.getNumber())
                .available(true)
                .timesBooked(0)
                .build();

        room = roomRepository.save(room);
        return roomMapper.toDto(room);
    }

    @Override
    @Transactional
    public boolean confirmAvailability(Long roomId, ConfirmAvailabilityRequest request) {
        log.info("Confirming availability for room {} with requestId {}",
                roomId, request.getRequestId());

        // Idempotency check
        Optional<RoomSlot> existingSlot = roomSlotRepository.findByRequestId(request.getRequestId());
        if (existingSlot.isPresent()) {
            log.info("Request {} already processed, returning success", request.getRequestId());
            return true;
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found with id: " + roomId));

        if (!room.getAvailable()) {
            log.warn("Room {} is not available", roomId);
            return false;
        }

        // Check for overlapping slots with pessimistic lock to prevent race conditions
        List<RoomSlot> overlappingSlots = roomSlotRepository.findOverlappingSlotsWithLock(
                roomId, request.getStartDate(), request.getEndDate());

        if (!overlappingSlots.isEmpty()) {
            log.warn("Room {} has overlapping booking for dates {} - {}",
                    roomId, request.getStartDate(), request.getEndDate());
            return false;
        }

        // Create slot
        RoomSlot slot = RoomSlot.builder()
                .room(room)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .requestId(request.getRequestId())
                .confirmed(true)
                .build();

        roomSlotRepository.save(slot);

        // Increment times booked
        room.setTimesBooked(room.getTimesBooked() + 1);
        roomRepository.save(room);

        log.info("Successfully confirmed availability for room {} with requestId {}",
                roomId, request.getRequestId());
        return true;
    }

    @Override
    @Transactional
    public void releaseSlot(String requestId) {
        log.info("Releasing slot with requestId {}", requestId);

        Optional<RoomSlot> slotOpt = roomSlotRepository.findByRequestId(requestId);
        if (slotOpt.isEmpty()) {
            log.info("No slot found for requestId {}, nothing to release", requestId);
            return;
        }

        RoomSlot slot = slotOpt.get();
        Room room = slot.getRoom();

        // Decrement times booked if it was confirmed
        if (slot.getConfirmed() && room.getTimesBooked() > 0) {
            room.setTimesBooked(room.getTimesBooked() - 1);
            roomRepository.save(room);
        }

        roomSlotRepository.delete(slot);
        log.info("Successfully released slot with requestId {}", requestId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoomStatisticsDto> getRoomStatistics(Long hotelId, String sortBy, String sortDir) {
        log.info("Getting room statistics for hotelId: {}, sortBy: {}, sortDir: {}",
                hotelId, sortBy, sortDir);

        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        String sortField = sortBy != null ? sortBy : "timesBooked";
        Sort sort = Sort.by(direction, sortField);

        List<Room> rooms;
        if (hotelId != null) {
            rooms = roomRepository.findByHotelId(hotelId, sort);
        } else {
            rooms = roomRepository.findAll(sort);
        }

        int maxTimesBooked = rooms.stream()
                .mapToInt(Room::getTimesBooked)
                .max()
                .orElse(1);

        return rooms.stream()
                .map(room -> RoomStatisticsDto.builder()
                        .roomId(room.getId())
                        .hotelId(room.getHotel().getId())
                        .hotelName(room.getHotel().getName())
                        .roomNumber(room.getNumber())
                        .timesBooked(room.getTimesBooked())
                        .occupancyRate(maxTimesBooked > 0
                                ? (double) room.getTimesBooked() / maxTimesBooked * 100
                                : 0.0)
                        .build())
                .toList();
    }
}
