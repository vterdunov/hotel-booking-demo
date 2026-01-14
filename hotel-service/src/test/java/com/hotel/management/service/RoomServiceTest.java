package com.hotel.management.service;

import com.hotel.management.dto.ConfirmAvailabilityRequest;
import com.hotel.management.dto.CreateRoomRequest;
import com.hotel.management.dto.RoomDto;
import com.hotel.management.entity.Hotel;
import com.hotel.management.entity.Room;
import com.hotel.management.entity.RoomSlot;
import com.hotel.management.mapper.RoomMapper;
import com.hotel.management.repository.HotelRepository;
import com.hotel.management.repository.RoomRepository;
import com.hotel.management.repository.RoomSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private RoomSlotRepository roomSlotRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private RoomMapper roomMapper;

    @InjectMocks
    private RoomService roomService;

    private Hotel hotel;
    private Room room;

    @BeforeEach
    void setUp() {
        hotel = Hotel.builder()
                .id(1L)
                .name("Test Hotel")
                .address("Test Address")
                .build();

        room = Room.builder()
                .id(1L)
                .hotel(hotel)
                .number("101")
                .available(true)
                .timesBooked(0)
                .build();
    }

    @Test
    @DisplayName("Should create room successfully")
    void createRoom_Success() {
        CreateRoomRequest request = CreateRoomRequest.builder()
                .hotelId(1L)
                .number("101")
                .build();

        RoomDto expectedDto = RoomDto.builder()
                .id(1L)
                .hotelId(1L)
                .number("101")
                .build();

        when(hotelRepository.findById(1L)).thenReturn(Optional.of(hotel));
        when(roomRepository.save(any(Room.class))).thenReturn(room);
        when(roomMapper.toDto(room)).thenReturn(expectedDto);

        RoomDto result = roomService.createRoom(request);

        assertThat(result).isNotNull();
        assertThat(result.getNumber()).isEqualTo("101");
        verify(roomRepository).save(any(Room.class));
    }

    @Test
    @DisplayName("Should confirm availability successfully")
    void confirmAvailability_Success() {
        ConfirmAvailabilityRequest request = ConfirmAvailabilityRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .requestId("req-123")
                .build();

        when(roomSlotRepository.findByRequestId("req-123")).thenReturn(Optional.empty());
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomSlotRepository.findOverlappingSlotsWithLock(any(), any(), any()))
                .thenReturn(Collections.emptyList());

        boolean result = roomService.confirmAvailability(1L, request);

        assertThat(result).isTrue();
        verify(roomSlotRepository).save(any(RoomSlot.class));
        verify(roomRepository).save(room);
        assertThat(room.getTimesBooked()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return true for idempotent request")
    void confirmAvailability_Idempotent() {
        ConfirmAvailabilityRequest request = ConfirmAvailabilityRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .requestId("req-123")
                .build();

        RoomSlot existingSlot = RoomSlot.builder()
                .id(1L)
                .requestId("req-123")
                .build();

        when(roomSlotRepository.findByRequestId("req-123")).thenReturn(Optional.of(existingSlot));

        boolean result = roomService.confirmAvailability(1L, request);

        assertThat(result).isTrue();
        verify(roomRepository, never()).save(any());
        verify(roomSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return false when room has overlapping slot")
    void confirmAvailability_OverlappingSlot() {
        ConfirmAvailabilityRequest request = ConfirmAvailabilityRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .requestId("req-123")
                .build();

        RoomSlot existingSlot = RoomSlot.builder()
                .id(2L)
                .room(room)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .requestId("existing-req")
                .build();

        when(roomSlotRepository.findByRequestId("req-123")).thenReturn(Optional.empty());
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(roomSlotRepository.findOverlappingSlotsWithLock(any(), any(), any()))
                .thenReturn(List.of(existingSlot));

        boolean result = roomService.confirmAvailability(1L, request);

        assertThat(result).isFalse();
        verify(roomSlotRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should return false when room is not available")
    void confirmAvailability_RoomNotAvailable() {
        room.setAvailable(false);

        ConfirmAvailabilityRequest request = ConfirmAvailabilityRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .requestId("req-123")
                .build();

        when(roomSlotRepository.findByRequestId("req-123")).thenReturn(Optional.empty());
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        boolean result = roomService.confirmAvailability(1L, request);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should release slot successfully")
    void releaseSlot_Success() {
        room.setTimesBooked(1);
        RoomSlot slot = RoomSlot.builder()
                .id(1L)
                .room(room)
                .requestId("req-123")
                .confirmed(true)
                .build();

        when(roomSlotRepository.findByRequestId("req-123")).thenReturn(Optional.of(slot));

        roomService.releaseSlot("req-123");

        verify(roomSlotRepository).delete(slot);
        verify(roomRepository).save(room);
        assertThat(room.getTimesBooked()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should do nothing when releasing non-existent slot")
    void releaseSlot_NotFound() {
        when(roomSlotRepository.findByRequestId("req-123")).thenReturn(Optional.empty());

        roomService.releaseSlot("req-123");

        verify(roomSlotRepository, never()).delete(any());
    }
}
