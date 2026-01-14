package com.hotel.booking.service;

import com.hotel.booking.client.HotelServiceClient;
import com.hotel.booking.client.RoomDto;
import com.hotel.booking.dto.BookingDto;
import com.hotel.booking.dto.CreateBookingRequest;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.Role;
import com.hotel.booking.entity.User;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class BookingServiceE2ETest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingMapper bookingMapper;

    @MockBean
    private HotelServiceClient hotelServiceClient;

    private User testUser;
    private String authToken = "test-token";

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(User.builder()
                .username("e2euser")
                .password("password")
                .role(Role.USER)
                .build());
    }

    @Test
    @DisplayName("E2E: Full booking flow - register, book, confirm, cancel")
    void fullBookingFlow() {
        // Setup mocks
        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString(), anyString()))
                .thenReturn(true);
        doNothing().when(hotelServiceClient).releaseSlot(anyLong(), anyString(), anyString());

        // Step 1: Create booking
        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .autoSelect(false)
                .build();

        BookingDto booking = bookingService.createBooking(testUser.getId(), request, authToken);

        assertThat(booking).isNotNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(booking.getRoomId()).isEqualTo(1L);

        // Step 2: Get user bookings
        List<BookingDto> bookings = bookingService.getUserBookings(testUser.getId());
        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).getId()).isEqualTo(booking.getId());

        // Step 3: Get booking by ID
        BookingDto retrievedBooking = bookingService.getBookingById(booking.getId(), testUser.getId());
        assertThat(retrievedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        // Step 4: Cancel booking
        bookingService.cancelBooking(booking.getId(), testUser.getId(), authToken);

        // Verify cancelled
        BookingDto cancelledBooking = bookingService.getBookingById(booking.getId(), testUser.getId());
        assertThat(cancelledBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Verify release was called
        verify(hotelServiceClient).releaseSlot(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("E2E: Auto-select room booking")
    void autoSelectRoomBooking() {
        RoomDto recommendedRoom = RoomDto.builder()
                .id(42L)
                .hotelId(1L)
                .number("301")
                .timesBooked(0)
                .build();

        when(hotelServiceClient.getRecommendedRooms(any(), any(), anyString()))
                .thenReturn(List.of(recommendedRoom));
        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString(), anyString()))
                .thenReturn(true);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(3))
                .autoSelect(true)
                .build();

        BookingDto booking = bookingService.createBooking(testUser.getId(), request, authToken);

        assertThat(booking).isNotNull();
        assertThat(booking.getRoomId()).isEqualTo(42L);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    @DisplayName("E2E: Booking fails when room not available - compensation executed")
    void bookingFailsWithCompensation() {
        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString(), anyString()))
                .thenReturn(false);

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .autoSelect(false)
                .build();

        assertThatThrownBy(() ->
                bookingService.createBooking(testUser.getId(), request, authToken))
                .isInstanceOf(BookingService.RoomNotAvailableException.class);

        // Verify booking was created and cancelled
        List<BookingDto> bookings = bookingService.getUserBookings(testUser.getId());
        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }

    @Test
    @DisplayName("E2E: Booking fails on Hotel Service error - compensation executed")
    void bookingFailsOnServiceError() {
        when(hotelServiceClient.confirmAvailability(anyLong(), any(), any(), anyString(), anyString()))
                .thenThrow(new HotelServiceClient.HotelServiceException("Connection refused"));
        doNothing().when(hotelServiceClient).releaseSlot(anyLong(), anyString(), anyString());

        CreateBookingRequest request = CreateBookingRequest.builder()
                .roomId(1L)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(5))
                .autoSelect(false)
                .build();

        assertThatThrownBy(() ->
                bookingService.createBooking(testUser.getId(), request, authToken))
                .isInstanceOf(BookingService.BookingFailedException.class);

        // Verify compensation was attempted
        verify(hotelServiceClient).releaseSlot(anyLong(), anyString(), anyString());

        // Verify booking was cancelled
        List<BookingDto> bookings = bookingService.getUserBookings(testUser.getId());
        assertThat(bookings).hasSize(1);
        assertThat(bookings.get(0).getStatus()).isEqualTo(BookingStatus.CANCELLED);
    }
}
