package com.hotel.booking.service;

import com.hotel.booking.client.HotelServiceClient;
import com.hotel.booking.client.RoomDto;
import com.hotel.booking.dto.BookingDto;
import com.hotel.booking.dto.CreateBookingRequest;
import com.hotel.booking.entity.Booking;
import com.hotel.booking.entity.BookingStatus;
import com.hotel.booking.entity.User;
import com.hotel.booking.mapper.BookingMapper;
import com.hotel.booking.repository.BookingRepository;
import com.hotel.booking.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final BookingMapper bookingMapper;
    private final HotelServiceClient hotelServiceClient;

    @Transactional
    public BookingDto createBooking(Long userId, CreateBookingRequest request, String authToken) {
        log.info("Creating booking for user {} with request: {}", userId, request);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + userId));

        String requestId = UUID.randomUUID().toString();

        // Check for idempotent request - if we somehow get duplicate
        Optional<Booking> existingBooking = bookingRepository.findByRequestId(requestId);
        if (existingBooking.isPresent()) {
            log.info("Found existing booking with requestId {}", requestId);
            return bookingMapper.toDto(existingBooking.get());
        }

        // Determine room ID
        Long roomId = request.getRoomId();
        if (Boolean.TRUE.equals(request.getAutoSelect())) {
            log.info("Auto-selecting room for dates {} - {}",
                    request.getStartDate(), request.getEndDate());
            List<RoomDto> recommendedRooms = hotelServiceClient.getRecommendedRooms(
                    request.getStartDate(), request.getEndDate(), authToken);
            if (recommendedRooms.isEmpty()) {
                throw new NoRoomsAvailableException("No rooms available for the specified dates");
            }
            roomId = recommendedRooms.get(0).getId();
            log.info("Auto-selected room: {}", roomId);
        }

        if (roomId == null) {
            throw new IllegalArgumentException("Room ID is required when autoSelect is false");
        }

        // Step 1: Create booking in PENDING status
        Booking booking = Booking.builder()
                .user(user)
                .roomId(roomId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .status(BookingStatus.PENDING)
                .requestId(requestId)
                .build();

        booking = bookingRepository.save(booking);
        log.info("Created PENDING booking {} with requestId {}", booking.getId(), requestId);

        // Step 2: Confirm availability with Hotel Service
        try {
            boolean confirmed = hotelServiceClient.confirmAvailability(
                    roomId, request.getStartDate(), request.getEndDate(), requestId, authToken);

            if (confirmed) {
                // Success: Update to CONFIRMED
                booking.setStatus(BookingStatus.CONFIRMED);
                booking = bookingRepository.save(booking);
                log.info("Booking {} confirmed successfully", booking.getId());
            } else {
                // Room not available: Cancel and compensate
                booking.setStatus(BookingStatus.CANCELLED);
                booking = bookingRepository.save(booking);
                log.warn("Room {} not available, booking {} cancelled", roomId, booking.getId());
                throw new RoomNotAvailableException("Room is not available for the specified dates");
            }
        } catch (HotelServiceClient.HotelServiceException e) {
            // Error/Timeout: Cancel and compensate
            log.error("Error confirming availability: {}", e.getMessage());
            booking.setStatus(BookingStatus.CANCELLED);
            bookingRepository.save(booking);

            // Try to release the slot (compensation)
            try {
                hotelServiceClient.releaseSlot(roomId, requestId, authToken);
            } catch (Exception releaseEx) {
                log.error("Failed to release slot during compensation: {}", releaseEx.getMessage());
            }

            throw new BookingFailedException("Failed to create booking: " + e.getMessage());
        }

        return bookingMapper.toDto(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> getUserBookings(Long userId) {
        return bookingMapper.toDtoList(bookingRepository.findByUserId(userId));
    }

    @Transactional(readOnly = true)
    public Page<BookingDto> getUserBookings(Long userId, Pageable pageable) {
        return bookingRepository.findByUserId(userId, pageable)
                .map(bookingMapper::toDto);
    }

    @Transactional(readOnly = true)
    public BookingDto getBookingById(Long bookingId, Long userId) {
        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Booking not found with id: " + bookingId));
        return bookingMapper.toDto(booking);
    }

    @Transactional
    public void cancelBooking(Long bookingId, Long userId, String authToken) {
        log.info("Cancelling booking {} for user {}", bookingId, userId);

        Booking booking = bookingRepository.findByIdAndUserId(bookingId, userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Booking not found with id: " + bookingId));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info("Booking {} is already cancelled", bookingId);
            return;
        }

        // Release slot in Hotel Service
        if (booking.getStatus() == BookingStatus.CONFIRMED) {
            try {
                hotelServiceClient.releaseSlot(
                        booking.getRoomId(), booking.getRequestId(), authToken);
            } catch (Exception e) {
                log.error("Failed to release slot for booking {}: {}",
                        bookingId, e.getMessage());
            }
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.info("Booking {} cancelled successfully", bookingId);
    }

    public static class NoRoomsAvailableException extends RuntimeException {
        public NoRoomsAvailableException(String message) {
            super(message);
        }
    }

    public static class RoomNotAvailableException extends RuntimeException {
        public RoomNotAvailableException(String message) {
            super(message);
        }
    }

    public static class BookingFailedException extends RuntimeException {
        public BookingFailedException(String message) {
            super(message);
        }
    }
}
