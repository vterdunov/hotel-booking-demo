package com.hotel.booking.service;

import com.hotel.booking.dto.BookingDto;
import com.hotel.booking.dto.CreateBookingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingServiceInterface {

    BookingDto createBooking(Long userId, CreateBookingRequest request, String authToken);

    List<BookingDto> getUserBookings(Long userId);

    Page<BookingDto> getUserBookings(Long userId, Pageable pageable);

    BookingDto getBookingById(Long bookingId, Long userId);

    void cancelBooking(Long bookingId, Long userId, String authToken);
}
