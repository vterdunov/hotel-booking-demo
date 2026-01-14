package com.hotel.booking.service;

import com.hotel.booking.client.HotelServiceClient;
import com.hotel.booking.client.RoomDto;
import com.hotel.booking.dto.BookingDto;
import com.hotel.booking.dto.CreateBookingRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.Role;
import com.hotel.booking.entity.User;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingMapper bookingMapper;

    @Mock
    private HotelServiceClient hotelServiceClient;

    @InjectMocks
    private BookingService bookingService;

    private User user;
    private String authToken;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .username("testuser")
                .password("password")
                .role(Role.USER)
                .build();
        authToken = "test-token";
    }

    @Test
    @DisplayName("Should create booking successfully with explicit room ID")
    void createBooking_Success_ExplicitRoom() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .autoSelect(false)
                .build();

        Booking savedBooking = Booking.builder()
                .id(1L)
                .user(user)
                .roomId(1L)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(BookingStatus.CONFIRMED)
                .requestId("test-request")
                .build();

        BookingDto expectedDto = BookingDto.builder()
                .id(1L)
                .roomId(1L)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString(), anyString()))
                .thenReturn(true);
        when(bookingMapper.toDto(any(Booking.class))).thenReturn(expectedDto);

        BookingDto result = bookingService.createBooking(1L, request, authToken);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
    }

    @Test
    @DisplayName("Should create booking with auto-selected room")
    void createBooking_Success_AutoSelect() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .autoSelect(true)
                .build();

        RoomDto recommendedRoom = RoomDto.builder()
                .id(5L)
                .number("101")
                .build();

        Booking savedBooking = Booking.builder()
                .id(1L)
                .user(user)
                .roomId(5L)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(BookingStatus.CONFIRMED)
                .requestId("test-request")
                .build();

        BookingDto expectedDto = BookingDto.builder()
                .id(1L)
                .roomId(5L)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(hotelServiceClient.getRecommendedRooms(any(), any(), anyString()))
                .thenReturn(List.of(recommendedRoom));
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(hotelServiceClient.confirmAvailability(eq(5L), any(), any(), anyString(), anyString()))
                .thenReturn(true);
        when(bookingMapper.toDto(any(Booking.class))).thenReturn(expectedDto);

        BookingDto result = bookingService.createBooking(1L, request, authToken);

        assertThat(result).isNotNull();
        assertThat(result.getRoomId()).isEqualTo(5L);
        verify(hotelServiceClient).getRecommendedRooms(any(), any(), anyString());
    }

    @Test
    @DisplayName("Should cancel booking and release slot when confirmation fails")
    void createBooking_Compensation_OnConfirmationFailure() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .autoSelect(false)
                .build();

        Booking pendingBooking = Booking.builder()
                .id(1L)
                .user(user)
                .roomId(1L)
                .status(BookingStatus.PENDING)
                .requestId("test-request")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenReturn(pendingBooking);
        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString(), anyString()))
                .thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(1L, request, authToken))
                .isInstanceOf(BookingService.RoomNotAvailableException.class);

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
    }

    @Test
    @DisplayName("Should cancel booking and perform compensation on Hotel Service error")
    void createBooking_Compensation_OnHotelServiceError() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .autoSelect(false)
                .build();

        Booking pendingBooking = Booking.builder()
                .id(1L)
                .user(user)
                .roomId(1L)
                .status(BookingStatus.PENDING)
                .requestId("test-request")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(bookingRepository.save(any(Booking.class))).thenReturn(pendingBooking);
        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString(), anyString()))
                .thenThrow(new HotelServiceClient.HotelServiceException("Timeout"));

        assertThatThrownBy(() -> bookingService.createBooking(1L, request, authToken))
                .isInstanceOf(BookingService.BookingFailedException.class);

        verify(hotelServiceClient).releaseSlot(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should throw exception when no rooms available for auto-select")
    void createBooking_NoRoomsAvailable_AutoSelect() {
        CreateBookingRequest request = CreateBookingRequest.builder()
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(3))
                .autoSelect(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookingRepository.findByRequestId(anyString())).thenReturn(Optional.empty());
        when(hotelServiceClient.getRecommendedRooms(any(), any(), anyString()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> bookingService.createBooking(1L, request, authToken))
                .isInstanceOf(BookingService.NoRoomsAvailableException.class)
                .hasMessageContaining("No rooms available");
    }

    @Test
    @DisplayName("Should cancel booking and release slot in Hotel Service")
    void cancelBooking_Success() {
        Booking booking = Booking.builder()
                .id(1L)
                .user(user)
                .roomId(1L)
                .status(BookingStatus.CONFIRMED)
                .requestId("test-request")
                .build();

        when(bookingRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(1L, 1L, authToken);

        verify(hotelServiceClient).releaseSlot(eq(1L), eq("test-request"), eq(authToken));
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("Should not release slot for already cancelled booking")
    void cancelBooking_AlreadyCancelled() {
        Booking booking = Booking.builder()
                .id(1L)
                .user(user)
                .roomId(1L)
                .status(BookingStatus.CANCELLED)
                .requestId("test-request")
                .build();

        when(bookingRepository.findByIdAndUserId(1L, 1L)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(1L, 1L, authToken);

        verify(hotelServiceClient, never()).releaseSlot(anyLong(), anyString(), anyString());
        verify(bookingRepository, never()).save(any());
    }
}
